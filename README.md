# samples

This collection of source files is meant to represent a high-level view of the architecture I use in my personal code library. It is not meant to provide a workable project that builds an executable artefact or to imply any particular codebase structure in which to do so.

The core pattern is referred to as the Manager pattern, which encapsulates all the main activities associated with a logical domain. Each logical domain in an application should have its own Manager implmentation.

File AppMgr.java implements application-level activities and represents the Application domain. It provides methods for creating and accessing Manager implmentations for other domains required by the application, e.g.:

private static UserManager userManager;
private static SecurityManager securityManager;
private static PlugInManager plugInManager;
 
Subclasses of the Application Manager represent the deployment style of the application, e.g. Thick Client, Thin Client, etc. and encapsulate activities specific to that style.

A set of Callback interfaces are used to instantiate the main application class, and this class then uses the Application Manager to access functionality in all the logical domains it supports, i.e. in additional Manager implementations like PlugInManager.

The Manager implementations handle CRUD requests, searches, and caching for the domain data. It interacts with a Security Manager to identify which data elements may be provided to the requester.

The CRUD interface provides a business-level (functional) view of the data and hides the details of accessing the data source. The Manager will employ DAO objects and services to access the desired backend data store. These are determined in configuration artefacts, usually files, and allow the Manager to change its DAO access method with a simple configuration update. The Manager will also decide whether to cache, what to cache and how to do that, and provides methods to the programmer to support any performance requirements that can be better attained via caching.

*** The Manager implementations ***

The *UserManager* provides the CRUD operations for managing the users of the application. The main application class will use this to support addition, update, and removal of registered users of the application. Other classes in the application will get user-specific information from the UserManager to carry out authentication and authorization activities, e.g. to log in to a database with the right credentials. This provides a more secure way of managing user data and takes away any temptation to hard-code user data within the application. The UserManager will store passwords or any sensitive infomration in an encrypted form, and will never store such data in clear text.

The *SecurityManager* provides the CRUD operations for managing the roles and permissions of the application. The application classes will make calls to the SecurityManager with the User details and knowledge of the requested operation, to see if the requester has the minimal access required to fulfill the request.

The *PlugInManager* supports dynamic loading of functionality that may be controlled via configuration artefacts. The units of functionality are PlugIns, or becasue they are adding functionality, they can also be called AddOns. An example is a Dictionary PlugIn that adds a menu option to look up terms specific to the application. As long as the PlugIn class implements the plugin interfaces, any application can support this PlugIn by including those interfaces and the PlugInManager implementation.


