local xtdb = {}

local db = require('xtdb.db')
local error = require('xtdb.error')
local log = require('log')
local migrator = require('xtdb.migrator')
local response = require('xtdb.response')
local utils = require('xtdb.utils')
local validator = require('xtdb.validator')

function xtdb.setup(config)
  local api = {}

  --
  -- Bootstrap
  --

  config = validator.validate(config)
  db.configure(config).create_db()
  migrator.migrate(config)



  --
  -- Models
  --

  local tx_log = require('xtdb.model.tx_log').model(config)



  --
  -- API methods
  --

  function api.submit_tx(events)
    if validator.not_blank_string(events) then
      local tuple = tx_log.submit_tx(events)
      log.info("submit_tx: %s", utils.to_json(tuple))
      return response.ok(tx_log.serialize(tuple))
    else
      log.warn("submit_tx: invalid params - %s", events)
      return response.error(error.BAD_REQUEST)
    end

  end

  return api

end

return xtdb
