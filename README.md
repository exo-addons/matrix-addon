# eXo Matrix addon
This addon allows to create a Chat room in Matrix for each eXo space.

## Configuration of eXo platform
 - **exo.matrix.server.url**: The URL of the matrix (Synapse) server
 - **exo.matrix.access_token**: The access token to be used for all requests to the server. To get the token using Element :
   - Log in to the account you want to get the access token for. Click on the name in the top left corner, then "Settings".
   - Click the "Help & About" tab (left side of the dialog).
   - Scroll to the bottom and click on <click to reveal> part of Access Token.
   - Copy your access token and add it to the configuration file
 - **exo.matrix.server.name**: The Matrix server name, it may be retrieved from the server domain, but useful in case of development with local Matrix server
 - **exo.matrix.shared_secret_registration**: The shared registration token that will be used for creating new user accounts, it takes the value of the property **registration_shared_secret** that could be found inside the _homeserver.yaml_ configuration file of the Matrix/Synapse server

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
  app_name: my_branded_matrix_server
  enable_notifs: true
  notif_for_new_users: true
  client_base_url: "http://localhost/riot"
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
