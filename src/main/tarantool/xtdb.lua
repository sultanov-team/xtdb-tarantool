box.cfg {
  listen = 3301,
  log_level = 7, -- debug
  memtx_memory = 128 * 1024 * 1024 -- 128 Mb
}

local config = {
  spaces = {
    tx_log = 'xtdb_tx_log',
    kv_store = 'xtdb_kv_store',
    document_store = 'xtdb_document_store'
  }
}

xtdb = require('xtdb.init').setup(config)
