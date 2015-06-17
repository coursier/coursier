/**
 * Copyright (c) 2012-2015, jcabi.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the jcabi.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jcabi.aether;

import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Loggable;
import com.jcabi.log.Logger;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.TrackableBase;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.repository.DefaultMirrorSelector;
import org.sonatype.aether.util.repository.DefaultProxySelector;

/**
 * Resolver of dependencies for one artifact.
 *
 * <p>You need the following dependencies to have in classpath in order to
 * to work with this class:
 *
 * <pre>
 * org.sonatype.aether:aether-api:1.13.1
 * org.apache.maven:maven-core:3.0.3
 * </pre>
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.1.6
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 * @checkstyle ClassFanOutComplexity (500 lines)
 * @see <a href="http://sonatype.github.com/sonatype-aether/apidocs/overview-tree.html">JcabiAether 1.13.1 JavaDoc</a>
 * @see Classpath
 * @todo #11:30min Create an integration test for reading proxy information
 *  from maven settings.xml file that will mock an HTTP server
 *  using jcabi-http, provide a maven configuration file with proxy settings
 *  and verify the expected HTTP requests have been sent to the mocked server.
 */
@Loggable(Loggable.DEBUG)
@SuppressWarnings("PMD.ExcessiveImports")
@Immutable
public class JcabiAether {

    /**
     * Remote project repositories.
     */
    @Immutable.Array
    final transient Repository[] remotes;

    /**
     * Location of lrepo repository.
     */
    final transient String lrepo;

    /**
     * Public ctor, requires information about all remote repositories and one
     * lrepo.
     * @param prj The Maven project
     * @param repo Local repository location (directory path)
     */
    public JcabiAether(@NotNull final MavenProject prj, @NotNull final File repo) {
        this(prj.getRemoteProjectRepositories(), repo);
    }

    /**
     * Public ctor, requires information about all remote repositories and one
     * lrepo.
     * @param repos Collection of remote repositories
     * @param repo Local repository location (directory path)
     * @since 0.8
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public JcabiAether(@NotNull final Collection<RemoteRepository> repos,
        @NotNull final File repo) {
        final Collection<Repository> rlist = new LinkedList<Repository>();
        for (final RemoteRepository remote : this.prepos(this.mrepos(repos))) {
            rlist.add(new Repository(remote));
        }
        this.remotes = rlist.toArray(new Repository[repos.size()]);
        this.lrepo = repo.getAbsolutePath();
    }

    /**
     * List of transitive dependencies of the artifact.
     * @param root The artifact to work with
     * @param scope The scope to work with ("runtime", "test", etc.)
     * @return The list of dependencies
     * @throws DependencyResolutionException If can't fetch it
     */
    public List<Artifact> resolve(@NotNull final Artifact root,
        @NotNull final String scope) throws DependencyResolutionException {
        final DependencyFilter filter =
            DependencyFilterUtils.classpathFilter(scope);
        if (filter == null) {
            throw new IllegalStateException(
                String.format("failed to create a filter for '%s'", scope)
            );
        }
        return this.resolve(root, scope, filter);
    }

    /**
     * List of transitive dependencies of the artifact.
     * @param root The artifact to work with
     * @param scope The scope to work with ("runtime", "test", etc.)
     * @param filter The dependency filter to work with
     * @return The list of dependencies
     * @throws DependencyResolutionException If can't fetch it
     */
    public List<Artifact> resolve(@NotNull final Artifact root,
        @NotNull final String scope, @NotNull final DependencyFilter filter)
        throws DependencyResolutionException {
        final Dependency rdep = new Dependency(root, scope);
        final CollectRequest crq = this.request(rdep);
        final List<Artifact> deps = new LinkedList<Artifact>();
        final RepositorySystem system = new RepositorySystemBuilder().build();
        deps.addAll(
            this.fetch(
                system,
                this.session(system),
                new DependencyRequest(crq, filter)
            )
        );
        return deps;
    }

    /**
     * Build repositories taking mirrors into consideration.
     * @param repos Initial list of repositories.
     * @return List of repositories with mirrored ones.
     */
    Collection<RemoteRepository> mrepos(
        final Collection<RemoteRepository> repos) {
        final DefaultMirrorSelector selector = this.mirror(this.settings());
        final Collection<RemoteRepository> mrepos =
            new ArrayList<RemoteRepository>(repos.size());
        for (final RemoteRepository repo : repos) {
            final RemoteRepository mrepo = selector.getMirror(repo);
            if (mrepo == null) {
                mrepos.add(repo);
            } else {
                mrepos.add(mrepo);
            }
        }
        return mrepos;
    }

    /**
     * Build repositories with proxy if it is available.
     * @param repos List of repositories
     * @return List of repositories with proxy
     */
    Collection<RemoteRepository> prepos(
        final Collection<RemoteRepository> repos
    ) {
        final org.apache.maven.settings.Proxy proxy = this.settings()
            .getActiveProxy();
        if (proxy != null) {
            final DefaultProxySelector selector = new DefaultProxySelector();
            selector.add(
                new Proxy(
                    proxy.getProtocol(),
                    proxy.getHost(),
                    proxy.getPort(),
                    new Authentication(proxy.getUsername(), proxy.getPassword())
                ), proxy.getNonProxyHosts()
            );
            for (final RemoteRepository repo : repos) {
                repo.setProxy(selector.getProxy(repo));
            }
        }
        return repos;
    }

    /**
     * Fetch dependencies.
     * Catch of NPE is required because sonatype even when it can't resolve
     * given artifact tries to get its root and execute a method on it,
     * which is not possible and results in NPE. Moreover sonatype library
     * is not developed since 2011 so this bug won't be fixed.
     * @param system The repository system
     * @param session The session
     * @param dreq Dependency request
     * @return The list of dependencies
     * @throws DependencyResolutionException If can't fetch it
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    List<Artifact> fetch(final RepositorySystem system,
        final RepositorySystemSession session, final DependencyRequest dreq)
        throws DependencyResolutionException {
        final List<Artifact> deps = new LinkedList<Artifact>();
        try {
            Collection<ArtifactResult> results;
            synchronized (this.lrepo) {
                results = system.resolveDependencies(session, dreq)
                    .getArtifactResults();
            }
            for (final ArtifactResult res : results) {
                deps.add(res.getArtifact());
            }
        // @checkstyle IllegalCatch (1 line)
        } catch (final Exception ex) {
            throw new DependencyResolutionException(
                new DependencyResult(dreq),
                new IllegalArgumentException(
                    Logger.format(
                        "failed to load '%s' from %[list]s into %s",
                        dreq.getCollectRequest().getRoot(),
                        JcabiAether.reps(dreq.getCollectRequest().getRepositories()),
                        session.getLocalRepositoryManager()
                            .getRepository()
                            .getBasedir()
                    ),
                    ex
                )
            );
        }
        return deps;
    }

    /**
     * Create collect request.
     * @param root The root to start with
     * @return The request
     */
    private CollectRequest request(final Dependency root) {
        final CollectRequest request = new CollectRequest();
        request.setRoot(root);
        for (final Repository repo : this.remotes) {
            final RemoteRepository remote = repo.remote();
            if (!remote.getProtocol().matches("https?|file|s3")) {
                Logger.warn(
                    this,
                    "%s ignored (only S3, HTTP/S, and FILE are supported)",
                    repo
                );
                continue;
            }
            request.addRepository(remote);
        }
        return request;
    }

    /**
     * Convert a list of repositories into a list of strings.
     * @param repos The list of them
     * @return The list of texts
     */
    private static Collection<String> reps(
        final Collection<RemoteRepository> repos) {
        final Collection<String> texts = new ArrayList<String>(repos.size());
        final StringBuilder text = new StringBuilder();
        for (final RemoteRepository repo : repos) {
            final Authentication auth = repo.getAuthentication();
            text.setLength(0);
            text.append(repo.toString());
            if (auth == null) {
                text.append(" without authentication");
            } else {
                text.append(" with ").append(auth.toString());
            }
            texts.add(text.toString());
        }
        return texts;
    }

    /**
     * Create RepositorySystemSession.
     * @param system The repository system
     * @return The session
     */
    RepositorySystemSession session(final RepositorySystem system) {
        final LocalRepository local = new LocalRepository(this.lrepo);
        final MavenRepositorySystemSession session =
            new MavenRepositorySystemSession();
        session.setLocalRepositoryManager(
            system.newLocalRepositoryManager(local)
        );
        session.setTransferListener(new LogTransferListener());
        return session;
    }

    /**
     * Setup mirrors based on maven settings.
     * @param settings Settings to use.
     * @return Mirror selector.
     */
    private DefaultMirrorSelector mirror(final Settings settings) {
        final DefaultMirrorSelector selector =
            new DefaultMirrorSelector();
        final List<Mirror> mirrors = settings.getMirrors();
        Logger.warn(
            this,
            "mirrors: %s",
            mirrors
        );
        if (mirrors != null) {
            for (final Mirror mirror : mirrors) {
                selector.add(
                    mirror.getId(), mirror.getUrl(), mirror.getLayout(), false,
                    mirror.getMirrorOf(), mirror.getMirrorOfLayouts()
                );
            }
        }
        return selector;
    }

    /**
     * Provide settings from maven.
     * @return Maven settings.
     */
    private Settings settings() {
        final SettingsBuilder builder =
            new DefaultSettingsBuilderFactory().newInstance();
        final SettingsBuildingRequest request =
            new DefaultSettingsBuildingRequest();
        final String user =
            System.getProperty("org.apache.maven.user-settings");
        if (user == null) {
            request.setUserSettingsFile(
                new File(
                    new File(
                        System.getProperty("user.home")
                    ).getAbsoluteFile(),
                    "/.m2/settings.xml"
                )
            );
        } else {
            request.setUserSettingsFile(new File(user));
        }
        final String global =
            System.getProperty("org.apache.maven.global-settings");
        if (global != null) {
            request.setGlobalSettingsFile(new File(global));
        }
        final SettingsBuildingResult result;
        try {
            result = builder.build(request);
        } catch (final SettingsBuildingException ex) {
            throw new IllegalStateException(ex);
        }
        return this.invokers(builder, result);
    }

    /**
     * Apply maven invoker settings.
     * @param builder Settings builder.
     * @param result User and global settings.
     * @return User, global and invoker settings.
     */
    private Settings invokers(final SettingsBuilder builder,
        final SettingsBuildingResult result) {
        Settings main = result.getEffectiveSettings();
        final Path path = Paths.get(
            System.getProperty("user.dir"), "..", "interpolated-settings.xml"
        );
        if (Files.exists(path)) {
            final DefaultSettingsBuildingRequest irequest =
                new DefaultSettingsBuildingRequest();
            irequest.setUserSettingsFile(path.toAbsolutePath().toFile());
            try {
                final Settings isettings = builder.build(irequest)
                    .getEffectiveSettings();
                SettingsUtils.merge(isettings, main, TrackableBase.USER_LEVEL);
                main = isettings;
            } catch (final SettingsBuildingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return main;
    }
}
