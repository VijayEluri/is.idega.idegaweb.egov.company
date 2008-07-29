package is.idega.idegaweb.egov.fsk.data;

import com.idega.company.data.Company;
import com.idega.data.IDOEntity;
import com.idega.user.data.Group;

public interface Division extends IDOEntity, Group {

	/**
	 * @see is.idega.idegaweb.egov.fsk.data.DivisionBMPBean#getCompany
	 */
	public Company getCompany();

	/**
	 * @see is.idega.idegaweb.egov.fsk.data.DivisionBMPBean#isApproved
	 */
	public Boolean isApproved();

	/**
	 * @see is.idega.idegaweb.egov.fsk.data.DivisionBMPBean#setApproved
	 */
	public void setApproved(boolean approved);
}