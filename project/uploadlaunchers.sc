

/**
 * Uploads files to a GitHub repository
 *
 * @param generatedFiles List of local files / name in the repository, to upload
 * @param ghOrg GitHub organization of the repository to upload to
 * @param ghProj GitHub project name of the repository to upload to
 * @param branch Branch to upload to
 * @param ghToken GitHub token
 * @param message Commit message
 * @param dryRun Whether to run a dry run (print actions that would have been done, but don't push / upload anything)
 */