package is.idega.idegaweb.egov.company.business;

import is.idega.idegaweb.egov.accounting.business.CitizenBusiness;
import is.idega.idegaweb.egov.application.business.ApplicationBusinessBean;
import is.idega.idegaweb.egov.application.data.Application;
import is.idega.idegaweb.egov.company.EgovCompanyConstants;
import is.idega.idegaweb.egov.company.data.CompanyApplication;
import is.idega.idegaweb.egov.company.data.CompanyApplicationHome;
import is.idega.idegaweb.egov.company.data.CompanyEmployee;
import is.idega.idegaweb.egov.company.data.CompanyEmployeeHome;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.mail.MessagingException;

import com.idega.business.IBOLookup;
import com.idega.company.CompanyConstants;
import com.idega.company.data.Company;
import com.idega.core.accesscontrol.business.AccessController;
import com.idega.core.accesscontrol.business.LoginCreateException;
import com.idega.core.accesscontrol.business.LoginDBHandler;
import com.idega.core.accesscontrol.business.NotLoggedOnException;
import com.idega.core.accesscontrol.data.LoginTable;
import com.idega.core.contact.data.Email;
import com.idega.core.data.ICTreeNode;
import com.idega.data.IDOLookup;
import com.idega.data.IDOLookupException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.IWMainApplicationSettings;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.idegaweb.IWUserContext;
import com.idega.presentation.IWContext;
import com.idega.user.business.GroupBusiness;
import com.idega.user.business.GroupHelper;
import com.idega.user.business.NoEmailFoundException;
import com.idega.user.business.NoPhoneFoundException;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;
import com.idega.util.CoreUtil;
import com.idega.util.EncryptionType;
import com.idega.util.IWTimestamp;
import com.idega.util.ListUtil;
import com.idega.util.SendMail;
import com.idega.util.StringHandler;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;

public class CompanyApplicationBusinessBean extends ApplicationBusinessBean implements CompanyApplicationBusiness {

	private static final long serialVersionUID = 2473252235079303894L;
	private static final Logger logger = Logger.getLogger(CompanyApplicationBusinessBean.class.getName());

	@Override
	public CompanyApplication getApplication(String applicationId) {
		if (StringUtil.isEmpty(applicationId)) {
			return null;
		}

		Object primaryKey = applicationId;
		Application app = null;
		try {
			app = getApplication(primaryKey);
		} catch (FinderException e) {
			logger.log(Level.SEVERE, "Error getting application by ID: " + applicationId, e);
		}
		if (!(app instanceof CompanyApplication)) {
			logger.log(Level.SEVERE, "Application " + app + " is not company application!");
			return null;
		}

		return (CompanyApplication) app;
	}

	public CompanyApplication getApplication(Company company) {
		CompanyApplication app;
		try {
			app = getCompanyApplicationHome().findByCompany(company);
		} catch (FinderException e) {
			e.printStackTrace();
			return null;
		}
		return app;
	}

	@Override
	public IWBundle getBundle() {
		return IWMainApplication.getDefaultIWMainApplication().getBundle(EgovCompanyConstants.IW_BUNDLE_IDENTIFIER);
	}

	public String approveApplication(IWContext iwc, String applicationId) {
		if (!setStatusToCompanyApplication(applicationId, getCaseStatusGranted().getStatus())) {
			return null;
		}

		CompanyApplication compApp = getApplication(applicationId);
		if (compApp == null) {
			return null;
		}

		IWResourceBundle iwrb = getResourceBundle();
		StringBuilder subject = new StringBuilder(getMailSubjectStart(compApp));
		subject.append(iwrb.getLocalizedString("application_approved_mail_subject", "application was approved"));
		String text = iwrb.getLocalizedString("application_successfully_approved", "Application was successfully approved!");

		String adminPassword = makeAccountsForCompanyAdmins(iwc, compApp);
		if (StringUtil.isEmpty(adminPassword)) {
			return null;
		}
		
		compApp.setAdminUser(compApp.getAdminUser());
		compApp.store();
		
		if (adminPassword.equals(CoreConstants.MINUS)) {
			return text;	//	Company admin already exists
		}
		
		Email email = null;
		try {
			email = getUserBusiness(iwc).getUsersMainEmail(compApp.getAdminUser());
		} catch(Exception e) {
			e.printStackTrace();
		}
		if (email == null) {
			return null;
		}
		
		LoginTable login = LoginDBHandler.getUserLogin(compApp.getAdminUser());
		text = new StringBuilder(text).append("\n\r").append(getLoginCreatedInfo(iwc, login.getUserLogin(), adminPassword)).toString();
		
		sendMail(email, subject.toString(), text);
		
		return text;
	}
	
	public String getLoginCreatedInfo(IWContext iwc, String login, String password) {
		String portNumber = new StringBuilder(":").append(String.valueOf(iwc.getServerPort())).toString();
		String serverLink = StringHandler.replace(iwc.getServerURL(), portNumber, CoreConstants.EMPTY);
		
		IWResourceBundle iwrb = getResourceBundle(iwc);
		
		return new StringBuilder(iwrb.getLocalizedString("login_here", "Login here")).append(": ").append(serverLink).append("\n\r")
			.append(iwrb.getLocalizedString("your_user_name", "Your user name")).append(": ").append(login).append(", ")
			.append(iwrb.getLocalizedString("your_password", "your password")).append(": ").append(password).append(". ")
			.append(iwrb.getLocalizedString("we_recommend_to_change_password_after_login", "We recommend to change password after login!"))
		.toString();
	}
	
	private String makeAccountsForCompanyAdmins(IWApplicationContext iwac, CompanyApplication compApp) {
		Company company = compApp.getCompany();
		if (company == null) {
			return null;
		}
		
		User companyAdmin = compApp.getAdminUser();
		if (companyAdmin != null) {
			logger.log(Level.INFO, "Application " + compApp.getName() + " already has admin: " + companyAdmin.getName());
			return CoreConstants.MINUS;
		}
		
		Collection<Group> companies = compApp.getGroups();
		if (ListUtil.isEmpty(companies)) {
			return null;
		}
		
		Group rootGroupForCompanies = getRootGroupForCompanies(companies.iterator().next());
		if (rootGroupForCompanies == null) {
			return null;
		}
		
		UserBusiness userBusiness = null;
		try {
			userBusiness = getUserBusiness(IWMainApplication.getDefaultIWApplicationContext());
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		if (userBusiness == null) {
			return null;
		}
		
		String personalId = company.getPersonalID();
		try {
			companyAdmin = userBusiness.getUser(personalId);
		} catch (RemoteException e) {
		} catch (FinderException e) {
		}
		String name = company.getName();
		String password = LoginDBHandler.getGeneratedPasswordForUser();
		if (companyAdmin == null) {
			try {
				companyAdmin = userBusiness.createUserWithLogin(name, null, null, personalId, name, null, null, null,
						Integer.valueOf(rootGroupForCompanies.getId()), personalId, password, true, null, -1, null, true, true, EncryptionType.MD5);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (CreateException e) {
				e.printStackTrace();
			}
			if (companyAdmin == null) {
				return null;
			}
			
			User applicant = compApp.getApplicantUser();
			if (applicant == null) {
				logger.log(Level.SEVERE, "Unkown contact person for company: " + name);
				return null;
			}
			
			//	Copying email
			try {
				userBusiness.updateUserMail(companyAdmin, userBusiness.getUsersMainEmail(applicant).getEmailAddress());
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (CreateException e) {
				e.printStackTrace();
			} catch (NoEmailFoundException e) {
				e.printStackTrace();
			}
			//	Copying mobile phone
			try {
				userBusiness.updateUserMobilePhone(companyAdmin, userBusiness.getUsersMobilePhone(applicant).getNumber());
			} catch (EJBException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NoPhoneFoundException e) {
				e.printStackTrace();
			}
			//	Copying work phone
			try {
				userBusiness.updateUserWorkPhone(companyAdmin, userBusiness.getUsersWorkPhone(applicant).getNumber());
			} catch (EJBException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NoPhoneFoundException e) {
				e.printStackTrace();
			}
			companyAdmin.store();
		}
		else {
			LoginTable login = LoginDBHandler.getUserLogin(companyAdmin);
			if (login == null) {
				try {
					login = LoginDBHandler.createLogin(companyAdmin, personalId, password, true, null, -1, null, true, true, EncryptionType.MD5);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (login == null) {
					return null;
				}
			}
		}
		
		compApp.setAdminUser(companyAdmin);
		compApp.store();
		
		for (Group companyGroup: companies) {
			makeUserCompanyAdmin(iwac, companyAdmin, companyGroup);
		}
		
		return password;
	}
	
	private boolean isGroupCompanyType(Group group) {
		return group == null ? false : CompanyConstants.GROUP_TYPE_COMPANY.equals(group.getGroupType());
	}
	
	public Group getRootGroupForCompanies(Group company) {
		if (company == null) {
			return null;
		}
		
		ICTreeNode parentNode = company.getParentNode();
		if (parentNode == null && isGroupCompanyType(company)) {
			return company;	//	Provided group is ROOT group
		}
		
		GroupBusiness groupBusiness = null;
		try {
			groupBusiness = getGroupBusiness(IWMainApplication.getDefaultIWApplicationContext());
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		if (groupBusiness == null) {
			return null;
		}
		
		ICTreeNode rootNode = null;
		while (parentNode != null) {
			parentNode = parentNode.getParentNode();
			if (parentNode != null) {
				rootNode = parentNode;
			}
		}
		if (rootNode == null) {
			return null;
		}
		
		Group topGroup = null;
		try {
			topGroup = groupBusiness.getGroupByGroupID(Integer.valueOf(rootNode.getId()));
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (FinderException e) {
			e.printStackTrace();
		}
	
		return isGroupCompanyType(topGroup) ? topGroup : null;
	}
	
	public Group getRootGroupForCompanies(IWContext iwc) {
		GroupHelper groupHelper = ELUtil.getInstance().getBean(GroupHelper.class);
		Collection<Group> topGroups = groupHelper.getTopGroupsFromDomain(iwc);
		if (ListUtil.isEmpty(topGroups)) {
			return null;
		}
		
		Group topGroupForCompany = null;
		for (Group group: topGroups) {
			topGroupForCompany = getRootGroupForCompanies(group);
			if (topGroupForCompany != null) {
				return topGroupForCompany;
			}
		}
		return null;
	}

	public boolean rejectApplication(IWApplicationContext iwac, String applicationId, String explanationText) {
		if (!setStatusToCompanyApplication(applicationId, getCaseStatusDenied().getStatus())) {
			return false;
		}

		CompanyApplication compApp = getApplication(applicationId);
		if (compApp == null) {
			return false;
		}

		Email email = null;
		try {
			email = getUserBusiness(iwac).getUsersMainEmail(compApp.getAdminUser());
		} catch(Exception e) {
			e.printStackTrace();
		}
		if (email == null) {
			return false;
		}
		
		IWResourceBundle iwrb = getResourceBundle();
		StringBuilder subject = new StringBuilder(getMailSubjectStart(compApp));
		subject.append(iwrb.getLocalizedString("application_rejected_mail_subject", "application was rejected"));
		return sendMail(email, subject.toString(), explanationText);
	}

	private boolean setStatusToCompanyApplication(String applicationId, String status) {
		CompanyApplication compApp = getApplication(applicationId);
		if (compApp == null) {
			return false;
		}

		compApp.setStatus(status);
		compApp.store();

		return true;
	}

	public boolean sendEmail(String email, String subject, String text) {
		IWMainApplicationSettings settings = IWMainApplication.getDefaultIWMainApplication().getSettings();
		if (settings == null) {
			return false;
		}

		String from = settings.getProperty(CoreConstants.PROP_SYSTEM_MAIL_FROM_ADDRESS);
		String host = settings.getProperty(CoreConstants.PROP_SYSTEM_SMTP_MAILSERVER);
		if (StringUtil.isEmpty(from) || StringUtil.isEmpty(host)) {
			logger.log(Level.WARNING, "Cann't send email from: " + from + " via: " + host + ". Set properties for application!");
			return false;
		}
		try {
			SendMail.send(from, email, null, null, host, subject, text);
		} catch (MessagingException e) {
			logger.log(Level.WARNING, "Error sending mail!", e);
			return false;
		}

		return true;
	}

	private boolean sendMail(Email email, String subject, String text) {
		if (email == null || StringUtil.isEmpty(text)) {
			return false;
		}

		return sendEmail(email.getEmailAddress(), subject, text);
	}

	private IWResourceBundle getResourceBundle() {
		return getResourceBundle(null);
	}
	
	private IWResourceBundle getResourceBundle(IWContext iwc) {
		return getBundle().getResourceBundle(iwc == null ? CoreUtil.getIWContext() : iwc);
	}

	private String getMailSubjectStart(CompanyApplication compApp) {
		Locale locale = null;
		IWContext iwc = CoreUtil.getIWContext();
		if (iwc != null) {
			locale = iwc.getCurrentLocale();
		}
		if (locale == null) {
			locale = Locale.ENGLISH;
		}

		return new StringBuilder(getApplicationName(compApp, locale)).append(CoreConstants.COLON).append(CoreConstants.SPACE).toString();
	}

	private boolean isUserLogged(IWUserContext iwuc) {
		if (iwuc == null) {
			return false;
		}

		User user = null;
		try {
			user = iwuc.getCurrentUser();
		} catch (NotLoggedOnException e) {
			logger.log(Level.SEVERE, "User is not logged!", e);
		}

		return user == null ? false : true;
	}

	public boolean isCompanyAdministrator(IWContext iwc) {
		try {
			return isUserLogged(iwc) && iwc.getAccessController().hasRole(EgovCompanyConstants.COMPANY_ADMIN_ROLE, iwc);
		} catch (NotLoggedOnException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean isCompanyEmployee(IWContext iwc) {
		try {
			return isCompanyAdministrator(iwc) || iwc.getAccessController().hasRole(EgovCompanyConstants.COMPANY_EMPLOYEE_ROLE, iwc);
		} catch (NotLoggedOnException e) {
			e.printStackTrace();
		}
		return false;
	}

	public CompanyApplicationHome getCompanyApplicationHome() {
		try {
			return (CompanyApplicationHome) IDOLookup.getHome(CompanyApplication.class);
		} catch (RemoteException rme) {
			throw new RuntimeException(rme.getMessage());
		}
	}
	
	public boolean makeUserCompanyAdmin(IWApplicationContext iwac, User companyAdmin, Group company) {
		if (companyAdmin == null || company == null) {
			return false;
		}
		
		User oldAdmin = company.getModerator();
		if (oldAdmin != null) {
			
		}
		company.setModerator(companyAdmin);
		company.store();
		
		AccessController accessController = iwac.getIWMainApplication().getAccessController();
		accessController.addRoleToGroup(EgovCompanyConstants.COMPANY_ADMIN_ROLE, companyAdmin, iwac);
		
		return true;
	}
	
	public boolean makeUserCommonEmployee(IWApplicationContext iwac, User companyAdmin, Group company) {
		if (company == null) {
			return false;
		}
		company.setModerator(null);
		company.store();
		
		AccessController accessController = iwac.getIWMainApplication().getAccessController();
		accessController.removeRoleFromGroup(EgovCompanyConstants.COMPANY_ADMIN_ROLE, companyAdmin, iwac);
		
		return true;
	}

	/**
	 * @see is.idega.idegaweb.egov.company.business.CompanyApplicationBusiness#createLogginForUser(com.idega.presentation.IWContext, com.idega.user.data.User,
	 *  java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	public boolean createLogginForUser(IWContext iwc, User user, String phoneHome, String phoneWork, String email, String roleKey, boolean addToRootCitizenGroup)
					throws LoginCreateException {
		String password = null;
		LoginTable loginTable = null;
		try {
			password = LoginDBHandler.getGeneratedPasswordForUser();
			loginTable = LoginDBHandler.createLogin(user, user.getPersonalID(), password);
			LoginDBHandler.changeNextTime(loginTable, true);
			getUserBusiness(iwc).updateUserHomePhone(user, phoneHome);
			getUserBusiness(iwc).updateUserWorkPhone(user, phoneWork);

		} catch (RemoteException e) {
			if (loginTable != null) {
				logger.log(Level.SEVERE, "Error updating user information on login creating for user : " + user.getId(), e);
				logger.log(Level.SEVERE, "Deleting created login: " + loginTable.getUserLogin());
				LoginDBHandler.deleteLogin(loginTable);
			} else {
				logger.log(Level.SEVERE, "Error creating user login for user: " + user.getId(), e);
			}
			return false;
		}

		if (addToRootCitizenGroup) {
			try {
				Group acceptedCitizens = getCitizenBusiness().getRootAcceptedCitizenGroup();
				acceptedCitizens.addGroup(user, IWTimestamp.getTimestampRightNow());
				if (user.getPrimaryGroup() == null) {
					user.setPrimaryGroup(acceptedCitizens);
					user.store();
				}
				AccessController accessController = iwc.getIWMainApplication().getAccessController();
				accessController.addRoleToGroup(roleKey, acceptedCitizens, iwc);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error updating user group information on login creation for user: " + user.getId(), e);
				if (loginTable != null) {
					logger.log(Level.SEVERE, "Deleting created login: " + loginTable.getUserLogin());
					LoginDBHandler.deleteLogin(loginTable);
				}
				return false;
			}
		}

		sendEmail(email, getBundle().getLocalizedString("account_details", "Account details"), getBundle().getLocalizedString("user_name", "User name") + ": " +
				loginTable.getUserLogin() + "\n" + getBundle().getLocalizedString("password", "Password") + ": " + password);
		return true;
	}

	/**
	 * @see is.idega.idegaweb.egov.company.business.CompanyApplicationBusiness
	 * #getAvailableApplicationsForUser(com.idega.presentation.IWContext, com.idega.user.data.User)
	 */
	public Collection<Application> getAvailableApplicationsForUser(IWContext iwc, User user) throws FinderException {
		@SuppressWarnings("unchecked")
		Collection<Application> allApplications = getApplicationHome().findAll();

		Group rootGroupForCompanies = getRootGroupForCompanies(iwc);
		boolean companyAdmin = isCompanyAdministrator(iwc);
		boolean superAdmin = iwc.isSuperAdmin();
		Collection<Application> userApplicationList = new ArrayList<Application>();
		Collection<Group> appGroups = null;
		for (Application app : allApplications) {
			boolean appAdded = false;
			appGroups = app.getGroups();
			appGroups = ListUtil.isEmpty(appGroups) ? null : new ArrayList<Group>(appGroups);
			if (ListUtil.isEmpty(appGroups)) {
				logger.log(Level.INFO, "Application " + app.getName() + " has no groups!");
			}
			else {
				if (rootGroupForCompanies != null) {
					appGroups.add(rootGroupForCompanies);
				}
				for (Group group : appGroups) {
					try {
						if (!appAdded && (superAdmin || companyAdmin || getUserBusiness(iwc).isMemberOfGroup(Integer.valueOf(group.getId()), user))) {
							userApplicationList.add(app);
							appAdded = true;
						}
					} catch (NumberFormatException e) {
						logger.log(Level.SEVERE, "Error getting applications for userID: " + user.getId(), e);
					} catch (RemoteException e) {
						logger.log(Level.SEVERE, "Error getting applications for userID: " + user.getId(), e);
					}
				}
			}
		}
		return userApplicationList;
	}

	protected UserBusiness getUserBusiness(IWApplicationContext iwac) throws RemoteException {
		return (UserBusiness) IBOLookup.getServiceInstance(iwac, UserBusiness.class);
	}
	
	protected GroupBusiness getGroupBusiness(IWApplicationContext iwac) throws RemoteException {
		return (GroupBusiness) IBOLookup.getServiceInstance(iwac, GroupBusiness.class);
	}

	protected CitizenBusiness getCitizenBusiness() throws RemoteException {
		return (CitizenBusiness) this.getServiceInstance(CitizenBusiness.class);
	}
	
	@SuppressWarnings("unchecked")
	public Group getUserCompany(IWContext iwc, User user) {
		if (user == null && !isCompanyEmployee(iwc)) {
			return null;
		}
		
		UserBusiness userBusiness = null;
		try {
			userBusiness = (UserBusiness) IBOLookup.getServiceInstance(iwc, UserBusiness.class);
		} catch(Exception e) {
			e.printStackTrace();
		}
		if (userBusiness == null) {
			return null;
		}
		
		try {
			return getGroupByType(userBusiness.getUsersTopGroupNodesByViewAndOwnerPermissions(user, iwc), CompanyConstants.GROUP_TYPE_COMPANY);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private Group getGroupByType(Collection<Group> groups, String groupTypeKey) {
		if (ListUtil.isEmpty(groups) || StringUtil.isEmpty(groupTypeKey)) {
			return null;
		}
		
		Group groupFromRecursion = null;
		for (Group group: groups) {
			if (groupTypeKey.equals(group.getGroupType())) {
				return group;
			}
			
			groupFromRecursion = getGroupByType(group.getChildren(), groupTypeKey);
			if (groupFromRecursion != null) {
				return groupFromRecursion;
			}
		}
		
		return null;
	}

	public Collection<Application> getUserApplications(IWContext iwc, User user) {
		if (isCompanyAdministrator(iwc)) {
			try {
				return getAvailableApplicationsForUser(iwc, user);
			} catch (FinderException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		CompanyEmployeeHome compEmplHome = null;
		try {
			compEmplHome = (CompanyEmployeeHome) IDOLookup.getHome(CompanyEmployee.class);
		} catch (IDOLookupException e) {
			e.printStackTrace();
		}
		if (compEmplHome == null) {
			return null;
		}
		
		CompanyEmployee compEmployee = null;
		try {
			compEmployee = compEmplHome.findByUser(user);
		} catch (FinderException e) {
			e.printStackTrace();
		}
		if (compEmployee == null) {
			return null;
		}
		
		return compEmployee.getServices();
	}
	
}
