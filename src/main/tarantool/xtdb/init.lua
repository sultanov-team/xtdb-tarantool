local xtdb = {}

local db = require('xtdb.db')
local log = require('log')
local migrator = require('xtdb.migrator')
local response = require('xtdb.response')
local utils = require('xtdb.utils')
local validator = require('xtdb.validator')

function xtdb.setup(config)
  local api = {
    tx_log = {}
  }

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

  --
  -- [tx_log] submit_tx
  --

  function api.tx_log.submit_tx(tx_events)
    -- if tx_events is valid
    if validator.not_blank_string(tx_events) then
      local tx = tx_log.submit_tx(tx_events)
      local res = {
        tx_id = tx[tx_log.TX_ID],
        tx_time = tx[tx_log.TX_TIME]
      }
      log.info("[tx_log.submit_tx]: `tx`=%s", utils.to_json(res))
      return response.success(response.CREATED, res)
    end

    -- if tx_events is not valid
    log.error("[tx_log.submit_tx]: BAD REQUEST - `tx_events`=%s", tx_events)
    return response.failure(response.BAD_REQUEST)
  end



  --
  -- [tx_log] open_tx_log
  --

  function api.tx_log.open_tx_log(after_tx_id)
    -- if after_tx_id is not valid
    if validator.is_some(after_tx_id) and not validator.is_pos(after_tx_id) then
      log.error("[tx_log.open_tx_log]: BAD REQUEST - `after_tx_id`=%s", after_tx_id)
      return response.failure(response.BAD_REQUEST)
    end

    -- if after_tx_id is nil
    if validator.is_nil(after_tx_id) then
      log.warn("[tx_log.open_tx_log]: `after_tx_id`=0 (instead of %s)", after_tx_id)
      after_tx_id = 0
    end

    -- get txs
    local txs = tx_log.open_tx_log(after_tx_id)

    -- if tx_log is empty
    if validator.is_empty(txs) then
      log.error("[tx_log.open_tx_log]: `after_tx_id`=%s, `txs`=[]", after_tx_id)
      return response.failure(response.NOT_FOUND)
    end

    -- if tx_log is not empty
    local res = utils.map(function(tx)
      return {
        tx_id = tx[tx_log.TX_ID],
        tx_time = tx[tx_log.TX_TIME],
        tx_events = tx[tx_log.TX_EVENTS]
      }
    end, txs)
    log.info("[tx_log.open_tx_log]: `after_tx_id`=%s, `txs`=%s", after_tx_id, utils.to_json(res))
    return response.success(response.OK, res)
  end



  --
  -- [tx_log] latest_submitted_tx
  --

  function api.tx_log.latest_submitted_tx()
    local tx = tx_log.latest_submitted_tx()

    -- if tx is not nil
    if validator.is_some(tx) then
      local res = {
        tx_id = tx[tx_log.TX_ID]
      }
      log.info("[tx_log.latest_submitted_tx]: `tx`=%s", utils.to_json(res))
      return response.success(response.OK, res)
    end

    -- if tx is nil
    log.error("[tx_log.latest_submitted_tx]: `tx`=nil")
    return response.failure(response.NOT_FOUND)
  end

  return api

end

return xtdb
