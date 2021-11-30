box.cfg {
  listen = 3301,
  log_level = 7, -- debug
  memtx_memory = 128 * 1024 * 1024 -- 128 Mb
}

-- API user will be able to login with this password
box.schema.user.create('root', { password = 'root' })
-- API user will be able to create spaces, add or remove data, execute functions
box.schema.user.grant('root', 'read,write,execute', 'universe')
