// variables that will be get from the server
const MATRIX_SERVER_URL='http://localhost:8008';
const USER_JWT='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbGkifQ.NDQLX7q8nSbr32SFjRa_AkzzvF1B4N0WmOPdYe81kQw';
const JWT_COOKIE_NAME = 'matrix_jwt_token';


export function checkAuthenticationTypes() {
  return fetch('/_matrix/client/r0/login', {
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  }).then(resp => {
    console.log(resp);
    return resp.flows.some(flow => flow.type === 'org.matrix.login.jwt');
  });
}

export function getCookieValue(name) {
    const regex = new RegExp(`(^| )${name}=([^;]+)`)
    const match = document.cookie.match(regex)
    if (match) {
      return match[2]
    }
}

export function authenticate() {
  const JWT = getCookieValue(JWT_COOKIE_NAME);
  if(JWT) {
    return fetch(`/_matrix/client/r0/login`, {
      method: 'POST',
      body: JSON.stringify({
        'type':'org.matrix.login.jwt',
        'token': JWT
      })
    }).then(resp => {
      if (!resp || !resp.ok) {
        throw new Error('Response code indicates a server error', resp);
      } else {
        return resp.json();
      }
    });
  } else {
    throw new Error('Could not find the JWT token');
  }
}
export function loadChatRooms() {
  //const headers = {'Authorization' : `Bearer $localStorage.getItem('matrix_access_token')};
  return fetch(`/_matrix/client/v3/joined_rooms`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
export function loadRoom(roomId) {
  return fetch(`/_matrix/client/v3/directory/room/${roomId}`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
