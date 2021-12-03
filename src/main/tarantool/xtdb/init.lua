local xtdb = {}

local db = require('xtdb.db')
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

  function api.tx_log_submit_tx(tx_events)
    if validator.not_blank_string(tx_events) then
      local res = tx_log.submit_tx(tx_events)
      log.info("[tx_log] submit_tx: %s", utils.to_json(res))
      return response.success(response.CREATED, tx_log.serialize(res))
    else
      log.warn("[tx_log] submit_tx: bad request - %s", tx_events)
      return response.failure(response.BAD_REQUEST)
    end
  end

  function api.tx_log_latest_submitted_tx()
    local res = tx_log.latest_submitted_tx()
    if validator.is_some(res) then
      log.info("[tx_log] latest_submitted_tx: %s", utils.to_json(res))
      return response.success(response.OK, tx_log.serialize(res))
    else
      log.info("[tx_log] latest_submitted_tx: not found - %s", utils.to_json(res))
      return response.failure(response.NOT_FOUND)
    end
  end

  return api

end

return xtdb
