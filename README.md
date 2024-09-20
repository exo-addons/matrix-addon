# eXo Matrix addon
This addon allows to create a Chat room in Matrix for each eXo space.

## Configuration of eXo platform
 - **exo.matrix.server.url**: The URL of the matrix (Synapse) server
 - **exo.matrix.access_token**: The access token to be used for all requests to the server. Check the following section on how to get this token.
 - **exo.matrix.server.name**: The Matrix server name, it may be retrieved from the server domain, but useful in case of development with local Matrix server
 - **exo.matrix.shared_secret_registration**: The shared registration token that will be used for creating new user accounts, it takes the value of the property **registration_shared_secret** that could be found inside the _homeserver.yaml_ configuration file of the Matrix/Synapse server


## Get the access token
### Using Element
- Log in to the account you want to get the access token for. Click on the name in the top left corner, then "Settings".
- Click the "Help & About" tab (left side of the dialog).
- Scroll to the bottom and click on <click to reveal> part of Access Token.
- Copy your access token and add it to the configuration file
### Using bash commands (recommended)
Create a new system user using the following commands :
1- Open a bash session on the Matrix docker container, in our case the container name is **matrix-synapse-1**
 ```bash 
 docker exec -it matrix-synapse-1 bash
 ```
2- Create a new user with the **register_new_matrix_user** and when prompt, answer yes to make him an admin
```bash
 register_new_matrix_user -u exo -p exo -c /data/homeserver.yaml
```
3- Exit from the Matrix container

4- Use a web client (like curl or wget) to get an access token
```bash
curl -XPOST \                         
  -d '{"type":"m.login.password", "user":"exo", "password":"exo"}' \
  "http://localhost:8008/_matrix/client/r0/login"
```
5- You will get a response like the following. You need to take the value of the access token and use it to configure eXo platform server
```json
{
  "user_id":"@exo:matrix.exo.tn",
  "access_token":"syt_ZXhv_vmTZZQKEJmGqlndIHUiJ_3SF20T",
  "home_server":"matrix.exo.tn",
  "device_id":"NODUMMFEFO"
} 
```

## Enable Email in user accounts 
To enable the registration of new users and add emails to their accounts, it is needed to add the following properties to the _homeserver.yaml_ configuration file. 
 - **enable_3pid_changes** to true, to enable updating user email
 - **email** to configure SMTP and email notification
Here is a sample configuration 
```yaml
enable_3pid_changes: true
email:
  smtp_host: mail.acme.com
  smtp_port: 25
  force_tls: false
  require_transport_security: false
  enable_tls: false
  notif_from: "Your Friendly %(app)s homeserver <noreply@example.com>"
  app_name: eXo Matrix Chat # Name of the server that will appear in notifications
  enable_notifs: true
  notif_for_new_users: true
  client_base_url: "http://localhost/riot" # URL to the Matrix client (Element)
  validation_token_lifetime: 15m
  invite_client_location: https://app.element.io

  subjects:
    message_from_person_in_room: "[%(app)s] You have a message on %(app)s from %(person)s in the %(room)s room..."
    message_from_person: "[%(app)s] You have a message on %(app)s from %(person)s..."
    messages_from_person: "[%(app)s] You have messages on %(app)s from %(person)s..."
    messages_in_room: "[%(app)s] You have messages on %(app)s in the %(room)s room..."
    messages_in_room_and_others: "[%(app)s] You have messages on %(app)s in the %(room)s room and others..."
    messages_from_person_and_others: "[%(app)s] You have messages on %(app)s from %(person)s and others..."
    invite_from_person_to_room: "[%(app)s] %(person)s has invited you to join the %(room)s room on %(app)s..."
    invite_from_person: "[%(app)s] %(person)s has invited you to chat on %(app)s..."
    password_reset: "[%(server_name)s] Password reset"
    email_validation: "[%(server_name)s] Validate your email"

```

## Link a space to a Matrix room
This is useful when the space is already created.
A REST web service was implemented to link an existing space to a room or even to create the room on Matrix server.
### Service URL :
Here is the URl to link a Matrix room to an existing space
 - http://SERVER_DOMAIN/portal/rest/matrix/linkroom?spaceGroupId=my_new_space&roomId=ROOM_ID&create=true
Parameters :
- spaceGroupId : the group ID of the space that we want to link. We can get it from the space URL. it is the words between the **:spaces:** and the next **/** character. 
  For example : **Required** if the space URL is /portal/g/:spaces:support_team/ then the groupId is **support_team** 
- roomId : **Optional** the technical ID of the room on the Matrix server if it is already created
- create: **Ignored** if the parameter _roomId_ is not empty. If it has the value to _true_ if we want to create a room for the space defined by the parameter spaceGroupId. Defaults to false
