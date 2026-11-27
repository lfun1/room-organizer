AI Instructions

Building instructions: Execute all the commands you are doing and pause after every file you create. Include very verbose comments so I can follow what the code does and so I can ask questions. Only create files within  the folder `organization_app`, nowhere else.

Create a room organization app that tracks the inventory of items in a room with a hierarchical nesting structure. Support everything under functionality below. Build an Android app as simply as possible. 

More functionality will be added later, so make it easier to add new features and save data across versions of the app. First build v0 below, then I will tell you to make v1 and onwards later. Read through the example below. For unspecified details, use your best judgement.

Make sure the app runs locally and has persistent data across all launches of the app, storing data in a SQLite database. Make sure the data is stored in a way that allows it to be accessible across different iterations of the app.

The room organization app has the following UI:
- Model the organization app after Google Drive
- All icons have smoothed corners
- Home screen: box icons representing each of the different containers of room
- Hierarchical folders that user clicks into to get to contents of the box
- Double click to enter into a folder, drop down in upper right corner for editing, deleting
- Create folder/item should have a plus sign box on all screens
- Each folder and item should have the ability to use a dropdown to see where it is in the hierarchy

Functionality v0:
- Basic CRUD of folders and items
- Create folders inside folders
- Create items in folders
- Move folders and items with drag and drop, or manual nested hierarchy drop down
- Item and folder info: name, description, photo
- Edit, delete items and folders. Include a warning before delete
- Navigate into and out of folders
- Each folder should have small icons indicating: number of items

Functionality v1:
- Global Search
- Filtering search: Add additional fields to items to filter/search by: category, quantity, condition

Functionality v2:
- Image recognition (with AI) to recognize images and autofill: name, description, categories (multiple), quantity, condition with ability to edit those fields

Functionality v3:
- Analysis on dashboard about items
- Suggest similar items, which items can be thrown away

Example v0:
1. Home page, check top right plus sign to create a folder "Closet"
2. Click inside and create an item "Rain Jacket"
3. Create a new folder on Home page called "Room"
4. Move "Closet" and all its contents into "Room"
5. Click on "Rain Jacket" to see details: name, description, photo (if provided)
6. Click on "Closet" folder to see details: name, description, photo. Then below that, show small icons of items inside it.
7. Delete "Closet" folder, which deletes all items inside it, including "Rain Jacket"
8. Then create "Large Drawer" folder inside "Room folder
