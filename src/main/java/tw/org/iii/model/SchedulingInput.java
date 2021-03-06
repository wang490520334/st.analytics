package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SchedulingInput 
{
	private List<String> cityList = new ArrayList<String>();
	private List<String> preferenceList = new ArrayList<String>();
	private Date startTime;
	
	private Date endTime;
	
	private GeoPoint gps;
	
	private String startPoiId;
	
	private String endPoiId;

	private List<String> mustPoiList = new ArrayList<String>();
	
	private boolean shake;

	private String tourType;
	
	private int looseType;
	
	public int getLooseType() {
		return looseType;
	}

	public void setLooseType(int looseType) {
		this.looseType = looseType;
	}

	public List<String> getCityList() {
		return cityList;
	}

	public void setCityList(List<String> cityList) {
		this.cityList = cityList;
	}

	public List<String> getPreferenceList() {
		return preferenceList;
	}

	public void setPreferenceList(List<String> preferenceList) {
		this.preferenceList = preferenceList;
	}

	public List<String> getMustPoiList() {
		return mustPoiList;
	}

	public void setMustPoiList(List<String> mustPoiList) {
		this.mustPoiList = mustPoiList;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public GeoPoint getGps() {
		return gps;
	}

	public void setGps(GeoPoint gps) {
		this.gps = gps;
	}

	public String getStartPoiId() {
		return startPoiId;
	}

	public void setStartPoiId(String startPoiId) {
		this.startPoiId = startPoiId;
	}

	public String getEndPoiId() {
		return endPoiId;
	}

	public void setEndPoiId(String endPoiId) {
		this.endPoiId = endPoiId;
	}

	public boolean isShake() {
		return shake;
	}

	public void setShake(boolean shake) {
		this.shake = shake;
	}

	public String getTourType() {
		return tourType;
	}

	public void setTourType(String tourType) {
		this.tourType = tourType;
	}
	

}
