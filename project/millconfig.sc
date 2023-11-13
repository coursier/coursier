interp.configureCompiler { c =>
  c.settings.nowarnings.value = false
  c.settings.deprecation.value = true
  c.settings.maxwarns.value = 100
}
