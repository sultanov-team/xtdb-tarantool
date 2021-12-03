local response = {}

-- success responses

response.OK = 200
response.CREATED = 201



-- error responses
response.BAD_REQUEST = 400
response.NOT_FOUND = 404



-- response codes
response.CODES = {

  -- success responses
  [response.OK] = 'Success',
  [response.CREATED] = 'Created',


  -- error responses
  [response.BAD_REQUEST] = 'Bad request',
  [response.NOT_FOUND] = 'Not found'

}

function response.failure(code, error)
  return {
    status = code,
    message = response.CODES[code],
    error = error
  }
end

function response.success(code, data)
  return {
    status = code,
    message = response.CODES[code],
    data = data
  }
end

return response
