Fetch / update the stats with
```bash
$ SONATYPE_PROJECT=io.get-coursier \
  SONATYPE_USERNAME=… \
  SONATYPE_PASSWORD=… \
  coursier launch com.lihaoyi:ammonite_2.12.8:1.6.3 \
    -M ammonite.Main \
    -- \
      sonatype-stats.sc
```

Update the plot with
```bash
$ rm -f plot.html
$ coursier launch com.lihaoyi:ammonite_2.12.8:1.6.3 \
    -M ammonite.Main \
    -- \
      plot.sc
```
