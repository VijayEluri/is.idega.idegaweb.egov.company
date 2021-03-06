package is.idega.idegaweb.egov.company.presentation;

import is.idega.idegaweb.egov.application.presentation.ApplicationBlock;
import is.idega.idegaweb.egov.company.EgovCompanyConstants;
import is.idega.idegaweb.egov.company.business.CompanyApplicationBusiness;
import is.idega.idegaweb.egov.company.business.CompanyPortalBusiness;

import java.util.logging.Level;

import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.company.data.Company;
import com.idega.core.location.data.Address;
import com.idega.core.location.data.PostalCode;
import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.presentation.IWContext;
import com.idega.presentation.Layer;
import com.idega.presentation.text.Heading1;
import com.idega.presentation.text.Heading3;
import com.idega.presentation.text.Link;
import com.idega.presentation.text.Text;
import com.idega.user.business.GroupBusiness;
import com.idega.user.data.Group;
import com.idega.util.PersonalIDFormatter;
import com.idega.util.PresentationUtil;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;

public abstract class CompanyBlock extends ApplicationBlock {
	
	protected IWBundle bundle;
	protected IWResourceBundle iwrb;
	
	protected Group group = null;
	
	protected CompanyApplicationBusiness getCompanyBusiness() {
		try {
			return (CompanyApplicationBusiness) IBOLookup.getServiceInstance(IWMainApplication.getDefaultIWApplicationContext(),
					CompanyApplicationBusiness.class);
		} catch (IBOLookupException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected CompanyPortalBusiness getCompanyPortalBusiness() {
		try {
			return ELUtil.getInstance().getBean(CompanyPortalBusiness.SPRING_BEAN_IDENTIFIER);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public String getBundleIdentifier() {
		return EgovCompanyConstants.IW_BUNDLE_IDENTIFIER;
	}
	
	@Override
	protected void present(IWContext iwc) throws Exception {
		bundle = getBundle(iwc);
		iwrb = bundle.getResourceBundle(iwc);
		PresentationUtil.addJavaScriptSourceLineToHeader(iwc, "/dwr/interface/CompanyApplicationBusiness.js");
		PresentationUtil.addJavaScriptSourceLineToHeader(iwc, "/dwr/engine.js");
		PresentationUtil.addStyleSheetToHeader(iwc, bundle.getVirtualPathWithFileNameString("style/egov_company.css"));
		PresentationUtil.addStyleSheetToHeader(iwc, iwc.getIWMainApplication().getBundle("is.idega.idegaweb.egov.application").getVirtualPathWithFileNameString("style/application.css"));
		
		if (group == null) {
			String groupId = null;
			if (iwc.isParameterSet(EgovCompanyConstants.COMPANY_PORTAL_CURRENT_COMPANY_ATTRIBUTE)) {
				groupId = iwc.getParameter(EgovCompanyConstants.COMPANY_PORTAL_CURRENT_COMPANY_ATTRIBUTE);
			}
			else if (iwc.getSessionAttribute(EgovCompanyConstants.COMPANY_PORTAL_CURRENT_COMPANY_ATTRIBUTE) != null) {
				groupId = iwc.getSessionAttribute(EgovCompanyConstants.COMPANY_PORTAL_CURRENT_COMPANY_ATTRIBUTE).toString();
			}
			if (!StringUtil.isEmpty(groupId)) {
				iwc.setSessionAttribute(EgovCompanyConstants.COMPANY_PORTAL_CURRENT_COMPANY_ATTRIBUTE, groupId);
				
				GroupBusiness groupBusiness = (GroupBusiness) IBOLookup.getServiceInstance(iwc, GroupBusiness.class);
				try {
					group = groupBusiness.getGroupByGroupID(Integer.valueOf(groupId));
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	protected Group getGroupThatIsCompanyForCurrentUser(IWContext iwc) {
		try {
			if (getCompanyBusiness().isCompanyEmployee(iwc)) {
				return getCompanyPortalBusiness().getCompanyGroupByUser(iwc.getCurrentUser());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	protected void showInsufficientRightsMessage(String message) {
		add(getInsufficientRightsMessage(message));
	}
	
	protected Layer getInsufficientRightsMessage(String message) {
		log(Level.WARNING, this.getClass().getName() + ": insufficient rigths!");
		return getMessage(message, "companyPortalInsufficientRigthsStyle");
	}
	
	protected void showMessage(String message) {
		showMessage(message, null);
	}
	
	protected void showMessage(String message, String styleClass) {	
		add(getMessage(message, styleClass));
	}
	
	protected Layer getMessage(String message) {
		return getMessage(message, null);
	}
	
	protected Layer getMessage(String message, String styleClass) {
		return getMessage(message, styleClass, false);
	}
	
	protected Layer getMessage(String message, String styleClass, boolean smallMessage) {
		Layer container = new Layer();
		if (!StringUtil.isEmpty(styleClass)) {
			container.setStyleClass(styleClass);
		}
		
		container.add(smallMessage ? new Heading3(message) : new Heading1(message));
		return container;
	}
	
	protected Layer getSmallMessage(String message, String styleClass) {
		return getMessage(message, styleClass, true);
	}
	
	protected Layer getCompanyInfo(IWContext iwc, Company company) {
		Layer layer = new Layer(Layer.DIV);
		layer.setStyleClass("info");

		if (company != null) {
			Address address = company.getAddress();
			PostalCode postal = null;
			if (address != null) {
				postal = address.getPostalCode();
			}

			Layer personInfo = new Layer(Layer.DIV);
			personInfo.setStyleClass("personInfo");
			personInfo.setID("name");
			personInfo.add(new Text(company.getName()));
			layer.add(personInfo);

			personInfo = new Layer(Layer.DIV);
			personInfo.setStyleClass("personInfo");
			personInfo.setID("personalID");
			personInfo.add(new Text(PersonalIDFormatter.format(company.getPersonalID(), iwc.getCurrentLocale())));
			layer.add(personInfo);

			personInfo = new Layer(Layer.DIV);
			personInfo.setStyleClass("personInfo");
			personInfo.setID("address");
			if (address != null) {
				personInfo.add(new Text(address.getStreetAddress()));
			}
			layer.add(personInfo);

			personInfo = new Layer(Layer.DIV);
			personInfo.setStyleClass("personInfo");
			personInfo.setID("postal");
			if (postal != null) {
				personInfo.add(new Text(postal.getPostalAddress()));
			}
			layer.add(personInfo);
		}

		return layer;
	}
	
	protected Link getButtonLink(String text) {
		Layer all = new Layer(Layer.SPAN);
		all.setStyleClass("buttonSpan");

		Layer left = new Layer(Layer.SPAN);
		left.setStyleClass("left");
		all.add(left);

		Layer middle = new Layer(Layer.SPAN);
		middle.setStyleClass("middle");
		middle.add(new Text(text));
		all.add(middle);

		Layer right = new Layer(Layer.SPAN);
		right.setStyleClass("right");
		all.add(right);

		Link link = new Link(all);
		link.setStyleClass("button");
		link.setToolTip(text);

		return link;
	}

}
