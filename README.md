# samples

This collection of source files is meant to represent a high-level view of the architecture I use in my personal code library.

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
  private static ServiceManager serviceManager;

Subclasses of the Application Manager represent the deployment style of the application, e.g. Thick Client, Thin Client, etc. and encapsulate activities specific to that style.

A set of Callback interfaces are used to instantiate the main application class, and this class then uses the Application Manager to access functionality in all the logical domains it supports.

