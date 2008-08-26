package is.idega.idegaweb.egov.company.presentation.employee;

import is.idega.idegaweb.egov.application.data.Application;
import is.idega.idegaweb.egov.company.presentation.CompanyBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.idega.presentation.IWContext;
import com.idega.presentation.Layer;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.Age;
import com.idega.util.ListUtil;

public class CompanyServicesViewer extends CompanyBlock {

	@SuppressWarnings("unchecked")
	@Override
	protected void present(IWContext iwc) throws Exception {
		super.present(iwc);

		if (!getCompanyBusiness().isCompanyEmployee(iwc)) {
			//	Add message that user has insufficient rights
			return;
		}
		
		Layer container = new Layer();
		add(container);

		User user = iwc.getCurrentUser();
		Collection<Application> allApplications = getCompanyBusiness().getApplicationHome().findAll();

		List<Application> userApplicationList = new ArrayList<Application>();
		for (Application app : allApplications) {
			boolean appAdded = false;
			for (Group group : app.getGroups()) {
				try {
					if (!appAdded && getUserBusiness(iwc).isMemberOfGroup(Integer.valueOf(group.getId()), user)) {
						userApplicationList.add(app);
						appAdded = true;
					}	
				} catch(NumberFormatException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (ListUtil.isEmpty(userApplicationList)) {
			//	TODO: add message that no services found
			return;
		}
		
		Age[] ages = null;
		boolean checkAges = false;
		ages = getApplicationBusiness(iwc).getAgesForUserAndChildren(user);
		checkAges = (ages != null);
		
		container.add(getApplicationList(iwc, checkAges, userApplicationList, ages));
	}
}
