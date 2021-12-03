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
    -- if tx_events is valid
    if validator.not_blank_string(tx_events) then
      -- FIXME: change to binary
      local res = tx_log.submit_tx(tx_events)
      log.info("[tx_log] submit_tx: %s", utils.to_json(res))
      return response.success(response.CREATED, tx_log.serialize(res))
    end

    -- if tx_events is not valid
    log.warn("[tx_log] submit_tx: bad request - %s", tx_events)
    return response.failure(response.BAD_REQUEST)
  end

  function api.tx_log_open_tx_log(after_tx_id)
    -- if after_tx_id is not valid
    if validator.is_some(after_tx_id) and not validator.is_pos(after_tx_id) then
      log.warn("[tx_log] open_tx_log: bad request - %s", utils.to_json(after_tx_id))
      return response.failure(response.BAD_REQUEST)
    end

    -- if after_tx_id is nil
    if validator.is_nil(after_tx_id) then
      log.warn("[tx_log] open_tx_log: apply `0` for `after_tx_id` instead of `%s`", utils.to_json(after_tx_id))
      after_tx_id = 0
    end

    -- get tx_log
    local coll = tx_log.open_tx_log(after_tx_id)

    -- if tx_log is empty
    if validator.is_empty(coll) then
      log.info("[tx_log] open_tx_log: not found - %s", utils.to_json(coll))
      return response.failure(response.NOT_FOUND)
    end

    -- if tx_log is not empty
    log.info("[tx_log] open_tx_log: %s", utils.to_json(coll))
    local res = utils.map(tx_log.serialize, coll)
    return response.success(response.OK, res)
  end

  function api.tx_log_latest_submitted_tx()
    local res = tx_log.latest_submitted_tx()

    -- if tx_log is not empty
    if validator.is_some(res) then
      log.info("[tx_log] latest_submitted_tx: %s", utils.to_json(res))
      return response.success(response.OK, tx_log.serialize(res))
    end

    -- if tx_log is empty
    log.warn("[tx_log] latest_submitted_tx: not found - %s", utils.to_json(res))
    return response.failure(response.NOT_FOUND)
  end

  return api

end

return xtdb
