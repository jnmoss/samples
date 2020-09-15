# samples

This collection of source files is meant to represent a high-level view of the architecture I use in my personal code library. It is not meant to provide a workable project that builds an executable artefact or to imply and particular codebase structure in which to do so.

The core pattern is referred to as the Manager pattern, which encapsulates all the main activities associated with a logical domain. Each logical domain in an application should have its own Manager implmentation.

File AppMgr.java implements application-level activities and represents the Application domain. It provides methods for creating and accessing Manager implmentations for other domains required by the application, e.g.:

  private static PlugInManager plugInManager;
  private static AddressBookManager addressBookManager;
  private static CommandManager commandManager;
  private static ConnectionManager connectionManager;
  private static FavoritesManager bookmarksManager;
  private static UserManager userManager;
  private static ContentManager contentManager;
  private static ReportManager reportManager;
  private static SearchManager searchManager;
  private static SecurityManager securityManager;
  private static ServiceManager serviceManager;

Subclasses of the Application Manager represent the deployment style of the application, e.g. Thick Client, Thin Client, etc. and encapsulate activities specific to that style.

A set of Callback interfaces are used to instantiate the main application class, and this class then uses the Application Manager to access functionality in all the logical domains it supports, i.e. in additional Manager implementations like PlugInManager.

The Manager implementations handle CRUD requests, searches, and caching for the domain data. It interacts with a Security Manager to identify which data elements may be provided to the requester.

The CRUD interface provides a business-level (functional) view of the data and hides the details of accessing the data source. The Manager will employ DAO objects and services to access the desired backend data store. These are determined in configuration artefacts, usually files, and allow the Manager to change its DAO access method with a simple configuration update. The Manager will also decide whether to cache, what to cache and how to do that, and provides methods to the programmer to support any performance requirements that can be better attained via caching.


