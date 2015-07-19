# RESTDemo

ATTENTION:
If you're looking for the GDAA implementation, it has been moved to:
https://github.com/seanpjanson/GDAADemo

RESTDemo is a minimal implementation of (S)CRUD functionality applied to the Google Drive REST API (REST)

https://developers.google.com/drive/web/about-sdk

The MainActivity shows how to handle the account switching and authorization. The pre-requisite here is a successful authorization of your app on the Developers console as described here:

https://developers.google.com/drive/android/auth

GDAADemo has 3 functions:

1/ UPLOAD uploads a text file to Google Drive, creating a simple tree directory structure in the process. The createTree() method allows for testing of different CRUD primitives (search, create folder, etc…).

2/ DOWNLOAD scans the tree created by the createTree() method. If the object is a file, it's metadata is updated (description field), the content is appended and the file is updated in the Google Drive.

3/ DELETE scans the tree created by the createTree() method and deletes all the files and folders in the process

Hope it helps.