package com.jmoss.kb.KBOSearch;

import com.jmoss.AppMgr;
import com.jmoss.AxThinCallback;
import com.jmoss.Constants;
import com.jmoss.data.DAO;
import com.jmoss.data.DAOFactory;
import com.jmoss.data.ServerDetails;
import com.jmoss.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.util.stream.Stream;

// Uncomment if does not pick up the properties file
// @PropertySource(value={"classpath:application-dev.properties"})

@SpringBootApplication
public class KboSearchApplication implements IxErrorClient {

	private static final String kName = KboSearchApplication.class.getSimpleName();
	private static final String kServerDelimiter = "_";

	private static KboSearchApplication instance = null;
	private static MyCallBack cb = null;
	private static Properties props = null;

	@Value("${spring.datasource.url}")
	private String dataSource;

	@Value("${spring.datasource.username}")
	private String username;

	@Value("${spring.datasource.password}")
	private String password;

	/**
	 **/
	public KboSearchApplication() {
		cb = new MyCallBack();
		cb.init();
		props = cb.getAppProperties();
		instance = this;
	}

	/**
	 **/
	private void initDAO() {
		try {
			ServerDetails server = ServerDetails.fromJDBC(dataSource, "spring.datasource.url");
			int daoType = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, "kbo-search.dao.type", String.valueOf(DAO.kTypeUnknown)));
			DAOFactory factory = DAOFactory.getDAOFactory(daoType, server.getName(), server.getComponent(), server.getPort(), username, password, false);
			if(factory != null) {
				// Set the Default DAO for the Application
				AppMgr.setDefaultDAO(factory.getGenericDAO());

				// Register this component as a user of the DAO type
				AppMgr.registerDaoClient(kName, daoType);
			}
		}
		catch(Exception e) {
			ErrorManager.addError(new ServerDetails(), Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false));
		}
	}

	/**
	 **/
	private static String getKey() {
		final StringBuilder buf = new StringBuilder(32);
		buf.append(AppMgr.getServerInstance());
		buf.append(kServerDelimiter);
		buf.append(Utils.key());
		return buf.toString().intern();
	}

	@Override
	public String errorEntryPrefix() {
		return kName;
	}

	@Override
	public String logEntryPrefix() {
		return kName;
	}

	@Override
	public String dump(int i) {
		return instance.toString();
	}

	/**
	 **/
	public static void main(String[] args) {
		try {
			ConfigurableApplicationContext context = SpringApplication.run(KboSearchApplication.class, args);
			System.out.println(context.toString());
		}
		catch(Exception e) {
			ErrorManager.addError(new ServerDetails(), Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false));
		}
	}

	/**
	 **/
	@Bean
	CommandLineRunner init(com.jmoss.kb.KBOSearch.repositories.KBORepository userRepository) {
		initDAO();
		return args -> {
			Stream.of("John", "Julie", "Jennifer", "Helen", "Rachel").forEach(name -> {
				com.jmoss.kb.KBOSearch.models.KBO user = new com.jmoss.kb.KBOSearch.models.KBO(getKey(), name);
				userRepository.save(user);
			});

			userRepository.findAll().forEach(System.out::println);
		};
	}

	/**
	 **/
	private final class MyCallBack extends AxThinCallback {
		private String appName = kName;
		private String appFolder = new File("").getAbsolutePath();
		private String productName = null;
		private String productFamilyName = null;
		private Properties props = new Properties(true);

		/**
		 **/
		public MyCallBack() {
			AppMgr.init(this, KboSearchApplication.class.getSimpleName());
		}

		@Override
		public Properties getAppProperties() {
			return props;
		}

		/**
		 **/
		public void init() {
			props.load(this, KboSearchApplication.this);
		}

		/**
		 * AxCallback
		 * @return
		 */
		@Override
		public String getProductName() {
			return productName == null ? appName : productName;
		}

		/**
		 **/
		private void setProductName(final String productName) {
			this.productName = productName;
		}

		/**
		 * AxCallback
		 * @return
		 */
		@Override
		public String getProductFamilyName() {
			return productFamilyName == null ? super.getProductFamilyName() : productFamilyName;
		}

		/**
		 **/
		private void setProductFamilyName(final String productFamilyName) {
			this.productFamilyName = productFamilyName;
		}

		@Override
		public String getApplicationFolder() {
			try {
				return Utils.kUserHome + File.separator + AppMgr.getDataSourceFileBranch() + File.separator + AppMgr.getSharedResString(Constants.kConfigFolder);
			}
			catch(Exception e) {
				return Utils.kUserHome + File.separator + getProductFamilyName() + File.separator + getProductName() + File.separator + AppMgr.getSharedResString(Constants.kConfigFolder);
			}
		}

		@Override
		public String getErrorPage() {
			return null;
		}

		@Override
		public String getInfoPage() {
			return null;
		}

		@Override
		public String getHomePage() {
			return null;
		}

		@Override
		public String getCurrentPage() {
			return null;
		}

		/**
		 **/
		public void logError(String msg) {
			LogMgr.logError(msg);
		}

		/**
		 **/
		public void logEvent(String msg) {
			LogMgr.logEvent(msg);
		}

		/**
		 **/
		public void logDebug(String msg) {
			LogMgr.logDebug(msg);
		}

		/**
		 * Log a debug event
		 * @param client
		 * @param ex
		 */
		public void logDebug(IxLogClient client, Throwable ex) {
			LogMgr.logDebug(client, ex);
		}

		/**
		 **/
		public void logTrace(Throwable ex) {
			LogMgr.logTrace(ex, false);
		}

		/**
		 **/
		public void logList(String list) {
			LogMgr.logList(list);
		}

		/**
		 **/
		public void logError(IxLogClient client, String msg) {
			LogMgr.logError(client, msg);
		}

		/**
		 **/
		public void logEvent(IxLogClient client, String msg) {
			LogMgr.logEvent(client, msg);
		}

		/**
		 **/
		public void logDebug(IxLogClient client, String msg) {
			LogMgr.logEvent(client, msg);
		}

		/**
		 **/
		public void logTrace(IxLogClient client, Throwable ex) {
			LogMgr.logTrace(client, ex, false);
		}

		/**
		 **/
		public void logDump(IxLogClient client) {
			LogMgr.logDump(client);
		}
	}
}
