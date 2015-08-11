package tw.org.iii.st.analytics.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.OptimzeInput;
import tw.org.iii.model.Poi;
import tw.org.iii.model.TourEvent;

@RestController
@RequestMapping("/OptimzeSchedule")
public class OptimzeSchedule {
	private OptimzeInput oi;

	@Autowired
	@Qualifier("hualienJdbcTempplate")
	private JdbcTemplate jdbcTemplate;

	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate stjdbcTemplate;

	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsJdbcTemplate;
	
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private static double MAGIC_NUM_FORLOOSE = 0.5;

	@RequestMapping(value="/Optimize", method = RequestMethod.POST)
	public @ResponseBody List<TourEvent> optimize(@RequestBody OptimzeInput aOptimzeInput) throws ParseException {
		//result
		ArrayList<TourEvent> optimizeSchedule = new ArrayList<TourEvent>();

		oi = aOptimzeInput;
		Date startTime = aOptimzeInput.getStartTime();
		Calendar cal;
		cal = Calendar.getInstance();
		cal.setTime(startTime);
		Date endTime;
		
		//oriScheduleStayTime
		double[] oriScheduleStayTime = getAllPoiStayTimeSQL();
		double[] resultScheduleStayTime = new double[oriScheduleStayTime.length];
		if(aOptimzeInput.getLooseType()==-1) {
			for(int i=0; i< oriScheduleStayTime.length; i++){
				System.out.println("stayTime_old:" + oriScheduleStayTime[i]);
				if(oriScheduleStayTime[i] > MAGIC_NUM_FORLOOSE)
				resultScheduleStayTime[i] = oriScheduleStayTime[i] - MAGIC_NUM_FORLOOSE;
				//System.out.println("stayTime:" + resultScheduleStayTime[i]);
			}
		} else if (aOptimzeInput.getLooseType()==1) {
			for(int i=0; i< oriScheduleStayTime.length; i++){
				if(oriScheduleStayTime[i] > MAGIC_NUM_FORLOOSE)
				resultScheduleStayTime[i] = oriScheduleStayTime[i] + MAGIC_NUM_FORLOOSE;
			}
		} else {
			
		}

		double[][] allPoiDis= getAllPoiDisSQL();
		ArrayList<Integer> scheduledIndex = new ArrayList<Integer>();
		/*for(int i=0; i<oi.getMustPoiList().size(); i++){
			scheduledIndex.add(i);
		}*/

		ArrayList<Integer> optScheduleIndex = new ArrayList<Integer>();
		//Start from first POI
		int closestIndex = 0;
		optScheduleIndex.add(0);
		scheduledIndex.add(0);
		while(true){
			closestIndex = findClosestPoiIndex(scheduledIndex, allPoiDis[closestIndex]);
			scheduledIndex.add(closestIndex);
			optScheduleIndex.add(closestIndex);

			if(optScheduleIndex.size()==aOptimzeInput.getMustPoiList().size())
				break;
		}

		//IN MINUTS.
		double[] transferTime= getAllTransferTime(optScheduleIndex);
		
		for(int i=0; i<optScheduleIndex.size();i++){
			//optimizeSchedule.add(i, new TourEvent(Date startTime, Date endTime, String poiId));
			System.out.println("output:" + i + ":" + oi.getMustPoiList().get(optScheduleIndex.get(i)).getPoiId());
			//optimizeSchedule.add(i, new TourEvent(null, null, oi.getMustPoiList().get(optScheduleIndex.get(i)).getPoiId()));
			endTime = addTime(startTime, resultScheduleStayTime[optScheduleIndex.get(i)] + transferTime[i]/60);
			optimizeSchedule.add(i,
					new TourEvent(startTime, 
							endTime, 
							oi.getMustPoiList().get(optScheduleIndex.get(i)).getPoiId()));
			
			System.out.println(" start:" + startTime + " end:" + endTime + " stay:" + resultScheduleStayTime[optScheduleIndex.get(i)] + " transfer:" + transferTime[i]);
			startTime = endTime;	
		}

		return optimizeSchedule;
	}

	public double[] getAllPoiStayTimeSQL() {
		String strPoiID = "";
		String sql = "";
		double[] allPoiStayTime = new double[oi.getMustPoiList().size()];

		/*for (String s : oi.getMustPoiList()) {
					strPoiID = strPoiID + "\"" + s + "\"" + ", " ;
				}*/
		for (Poi s : oi.getMustPoiList()) {
			strPoiID = strPoiID + "\"" + s.getPoiId() + "\"" + ", " ;
		}

		//CHECK THE LAST ","!!
		strPoiID = strPoiID.substring(0, strPoiID.lastIndexOf(","));
		//System.out.println("stayTime strPoiID: " + strPoiID);

		//EX: SELECT staytime FROM Statistics WHERE poiId IN ("9_02_2321_8928_1", "9_02_2361_5588") ORDER BY FIELD(poiId, "9_02_2361_5588", "9_02_2361_5588");
		sql = "SELECT staytime FROM Statistics WHERE poiId IN (" + strPoiID + ") ORDER BY FIELD(poiId, " + strPoiID + ")";
		System.out.println("SQL: " + sql);
		List<Double> tmpResult = stjdbcTemplate.queryForList(sql, Double.class);

		for(int i=0; i<tmpResult.size(); i++) {
			allPoiStayTime[i] = tmpResult.get(i);
			System.out.println("stayTime i: " + tmpResult.get(i));
		}
		return allPoiStayTime;
	}

	public double[][] getAllPoiDisSQL() {
		String sql = "";

		double[][] allPoiDis = new double[oi.getMustPoiList().size()][oi.getMustPoiList().size()];
		for (int i=0; i<oi.getMustPoiList().size(); i++) {
			for(int j=0; j<oi.getMustPoiList().size(); j++){

				if(i==j)
					continue;

				//START HERE! also get time
				sql = "SELECT distance FROM googledirection_hybrid WHERE id = '" + oi.getMustPoiList().get(i).getPoiId() + "' AND arrival_id = '" + oi.getMustPoiList().get(j).getPoiId() + "'";
				System.out.println("SQL: " + sql);

				try {
					//allPoiDis[i][j]	 = jdbcTemplate.queryForObject(sql, Double.class);
					String dis = jdbcTemplate.queryForObject(sql, String.class);
					dis = dis.replace(" 公里", "");
					allPoiDis[i][j] = Double.parseDouble(dis);
				} catch (EmptyResultDataAccessException e) {	
					List<GeoPoint> tmp = getTwoPoiGeoSQL(oi.getMustPoiList().get(i).getPoiId(), oi.getMustPoiList().get(j).getPoiId());
					//System.out.println("NULL SET!" + tmp.get(0).getLat() + "-" + tmp.get(0).getLng() + "-" +tmp.get(1).getLat() + "-" +tmp.get(1).getLng());
					allPoiDis[i][j] = getTwoPoiGeoDistance(tmp.get(0).getLat(),tmp.get(0).getLng(),tmp.get(1).getLat(),tmp.get(1).getLng());
					//System.out.println("NullTwoResult: id" + oi.getMustPoiList().get(i).getPoiId() + "-> " +oi.getMustPoiList().get(j).getPoiId() + "--" + allPoiDis[i][j]);
				}

				//System.out.println("Dis-i:" + i + "-j:" + j +": "+ allPoiDis[i][j]);
			}

		}
		return allPoiDis;
	}
	
	public double[][] getAllPoiGeoDistance(){
		double[][] allPoiDis = new double[oi.getMustPoiList().size()][oi.getMustPoiList().size()];
		for (int i=0; i<oi.getMustPoiList().size(); i++) {
			for(int j=0; j<oi.getMustPoiList().size(); j++){
				if(i==j)
					continue;
				List<GeoPoint> tmp = getTwoPoiGeoSQL(oi.getMustPoiList().get(i).getPoiId(), oi.getMustPoiList().get(j).getPoiId());
				allPoiDis[i][j] = getTwoPoiGeoDistance(tmp.get(0).getLat(),tmp.get(0).getLng(),tmp.get(1).getLat(),tmp.get(1).getLng());
			}

		}
		return allPoiDis;
	}

	public List<GeoPoint> getAllPoiGeoSQL() {
		String strPoiID = "";
		String sql = "";

		//CHECK THE LAST ","!!
		for (Poi s : oi.getMustPoiList()) {
			strPoiID = strPoiID + s.getPoiId() + ", " ;
		}

		//CHECK SQL: WHICH TABLE XXX!!
		sql = "SELECT px, py FROM place_part_zh_tw WHERE place_id IN ('" + strPoiID + "') ORDER BY FIELD(place_id, '" + strPoiID + "')";
		//System.out.println("SQL_TWOPOINT: " + sql);
		List<GeoPoint> allPoiGeo = jdbcTemplate.query(
				sql,
				new RowMapper<GeoPoint>() {
					@Override
					public GeoPoint mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new GeoPoint(rs.getDouble("px"), rs.getDouble("py"));
					}
				});

		return allPoiGeo;
	}

	public double[] getAllTransferTime(ArrayList<Integer> optScheduleIndex) {
		double[] resultList = new double[optScheduleIndex.size()];
		
		int pre;
		int next;
		for(int i=0; i<optScheduleIndex.size();i++) {
			if(i==optScheduleIndex.size()-1) {
				//DUMMY
				resultList[i] = Integer.MAX_VALUE;
				break;
			}
				
			pre = optScheduleIndex.get(i);
			next = optScheduleIndex.get(i+1);
			resultList[i] = getTwoPoiTansferSQL(oi.getMustPoiList().get(pre).getPoiId(), oi.getMustPoiList().get(next).getPoiId());
		}
		
		return resultList;
	}
	
	public double getTwoPoiTansferSQL(String startPoiID, String endPoiID) {
		String sql = "SELECT time FROM googledirection_hybrid WHERE id = '" + startPoiID + "' AND arrival_id = '" + endPoiID + "'";
		//System.out.println("SQLTRANSFER:" + sql);
		double time = 0;
		try {
			time = jdbcTemplate.queryForObject(sql, Double.class);
		} catch (EmptyResultDataAccessException e) {	
			List<GeoPoint> tmp = getTwoPoiGeoSQL(startPoiID, endPoiID);
			time = getTwoPoiGeoDistance(tmp.get(0).getLat(),tmp.get(0).getLng(),tmp.get(1).getLat(),tmp.get(1).getLng())/60;
		}

		return time;
	}
	
	
	public List<GeoPoint> getTwoPoiGeoSQL(String startPoiID, String endPoiID) {
		//String sql = "SELECT px, py FROM scheduling WHERE place_id IN ('" + startPoiID + "', '" + endPoiID + "') ORDER BY FIELD(place_id, '" + startPoiID + "', '" + endPoiID + "')";
		//String sql = "SELECT px, py FROM place_part_zh_tw WHERE place_id IN ('" + startPoiID + "', '" + endPoiID + "') ORDER BY FIELD(place_id, '" + startPoiID + "', '" + endPoiID + "')";
		String sql = "SELECT AsText(A.location) FROM Poi as A, Detail as B where A.detailId = B.id AND B.poiId IN ('" + startPoiID + "', '" + endPoiID + "') ORDER BY FIELD(B.poiId, '" + startPoiID + "', '" + endPoiID + "')";
		//System.out.println("SQLTWOPOINT:" + sql);

		List<String> twoGeo = stjdbcTemplate.queryForList(sql, String.class);
		List<GeoPoint> twoPoiGeo = new ArrayList<GeoPoint>();
		for(int i=0; i<twoGeo.size(); i++) {
			String[] tmp = twoGeo.get(i).replace("POINT(", "").replace(")", "").split(" ");	
			twoPoiGeo.add(new GeoPoint(Double.parseDouble(tmp[0]), Double.parseDouble(tmp[1])));
		}
		/*
		List<GeoPoint> twoPoiGeo = stjdbcTemplate.query(
				sql,
				new RowMapper<GeoPoint>() {
					@Override
					public GeoPoint mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						System.out.println("NUM:" + rowNum);

						return new GeoPoint(rs.getDouble("px"), rs.getDouble("py"));
					}
				});*/

		return twoPoiGeo;
	}
	
	

	public double getTwoPoiGeoDistance(double lat1, double lng1, double lat2, double lng2) {
		double EARTH_RADIUS = 6378137;
		double radLat1 = rad(lat1);
		double radLat2 = rad(lat2);
		double a = radLat1 - radLat2;
		double b = rad(lng1) - rad(lng2);
		double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
				+ Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
		s = s * EARTH_RADIUS;
		s = Math.round(s * 10000) / 10000;
		return s;
	}

	private double rad(double d) {
		return d * Math.PI / 180.0;
	}


	int findClosestPoiIndex(ArrayList<Integer> scheduledIndex, double[] aDistanceList) {
		int resultIndex = 0;
		double min = Integer.MAX_VALUE;
		for(int i=0; i<aDistanceList.length; i++) {
			if(scheduledIndex.contains(i))
				continue;

			if(aDistanceList[i] < min) {
				min = aDistanceList[i];
				resultIndex = i;
			}
		}

		//System.out.println( "Minimum of row = " + resultIndex );
		return resultIndex;
	}
	
	private Date addTime(Date d, double minute) throws ParseException {
		Calendar cal;
		cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.MINUTE, (int)minute);
		return sdf.parse(sdf.format(cal.getTime()));
	}
	/*
	//getMinimumOfEveryRow.
	public void findClosestPoiIndex (double[][] aDistanceMatrix) {
		for(int i=0; i<aDistanceMatrix.length; i++) {
			double min = Integer.MAX_VALUE;
			for(int j=0; j<aDistanceMatrix[i].length; j++){
				if(aDistanceMatrix[i][j] < min)
					min = aDistanceMatrix[i][j];
			}
			System.out.println( "Minimum of row " + i + " = " + min );
		}
	}
	 */
}