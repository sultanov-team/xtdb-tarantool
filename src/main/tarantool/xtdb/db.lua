local db = {}

function db.configure(config)
  local api = {}

  local tx_log = require('xtdb.model.tx_log').model(config)

  function api.create_db()
    box.once('schema', function()

      --
      -- tx_log
      --

      box.schema.sequence.create(tx_log.TX_ID_INDEX_SEQ, {
        start = 1,
        min = 1,
        step = 1,
        if_not_exists = true
      })

      local tx_log_space = box.schema.space.create(tx_log.SPACE_NAME, {
        if_not_exists = true
      })

      tx_log_space:format({
        { name = tx_log.TX_ID_FIELD, type = 'unsigned' },
        { name = tx_log.TX_TIME_FIELD, type = 'unsigned' },
        { name = tx_log.TX_EVENTS_FIELD, type = 'string' } -- FIXME: change to binary
      })

      tx_log_space:create_index(tx_log.TX_ID_INDEX, {
        parts = { tx_log.TX_ID_FIELD },
        sequence = tx_log.TX_ID_INDEX_SEQ,
        if_not_exists = true
      })

    end)
  end

  function api.truncate_db()
    tx_log.get_space():truncate()
  end

  return api

end

return db
