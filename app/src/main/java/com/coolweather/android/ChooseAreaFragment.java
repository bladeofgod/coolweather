package com.coolweather.android;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by 45010 on 2018/1/3.
 */

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTY=2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList=new ArrayList<>();
    //省列表
    private List<Province> provinceList;
    //市列表
    private List<City> cityList;
    //县列表
    private List<County> countyList;
    //选中的省份
    private Province selectedProvince;
    //选中的市
    private City selectedCity;
    //选中的省
    private County selectedCounty;
    //选中的级别
    private  int currentLevel;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        //初始化选择地区的界面view
        View view=inflater.inflate(R.layout.choose_area,container,false);
        titleText=(TextView)view.findViewById(R.id.title_text);
        backButton=(Button)view.findViewById(R.id.back_button);
        listView=(ListView)view.findViewById(R.id.list_view);
        //这里要引用import android.support.v4.app.Fragment; 否则getContext会报版本错误
        adapter=new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;

    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        //列表字段点击事件
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //i:position  l=id
                if (currentLevel==LEVEL_PROVINCE){
                    selectedProvince=provinceList.get(i);
                    queryCities();
                }else if (currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get(i);
                    queryCounties();
                }else if (currentLevel==LEVEL_COUNTY){
                    //若选中状态为县，则准备显示具体天气
                    String weatherId=countyList.get(i).getWeatherId();
                    //判断当前Activity是否是MainActivity
                    if (getActivity() instanceof MainActivity){
                        //通过Intent 将weatherId传给WeatherActivity
                        Intent intent=new Intent(getActivity(),WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if (getActivity() instanceof WeatherActivity){
                        WeatherActivity activity=(WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        //当当前在WeatherActivity时，则直接再次请求天气信息
                        activity.requestWeather(weatherId);
                    }

                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //后退按钮点击事件,从县退回到市，再到省
                if (currentLevel==LEVEL_COUNTY){
                    queryCities();
                }else if (currentLevel==LEVEL_CITY){
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }
    /*
    *查询全国所有的省，优先数据库查询，没有再去服务器查询
    * */
    private void queryProvinces(){
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        //findAll查询省的数据并初始化pList
        provinceList= DataSupport.findAll(Province.class);
        if (provinceList.size()>0){
            dataList.clear();
            for (Province province:provinceList){
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_PROVINCE;
        }else{
            //数据库没有，去服务器上获取
            String address="http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }
    /*
    * 查询省内的所有市
    * */
    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        //数据库查询语句,初始化市列表
        cityList=DataSupport.where("provinceid=?",String.valueOf(selectedProvince.getId()))
                .find(City.class);
        if (cityList.size()>0){
            dataList.clear();
            for (City city:cityList){
                dataList.add(city.getCityName());

            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_CITY;

        }else {
            int provinceCode=selectedProvince.getProvinceCode();
            String address="http://guolin.tech/api/china/"+provinceCode;
            queryFromServer(address,"city");
        }
    }

    /*
    * 查询市下面的县
    * */
    private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        //初始化县列表
        countyList=DataSupport.where("cityid=?",String.valueOf(selectedCity.getId()))
                .find(County.class);
        if (countyList.size()>0){
            dataList.clear();
            for (County county:countyList){
                dataList.add(county.getCountyName());

            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_COUNTY;
        }else {
            int provinceCode=selectedProvince.getProvinceCode();
            int cityCode=selectedCity.getCityCode();
            String address="http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            queryFromServer(address,"county");
        }
    }

    /*
    * 根据传入的地址和数据类型，从服务器上获取数据
    * */

    private void queryFromServer(String address,final String type){
        //进度条
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //因为需要对UI进行操作，所以需要通过runOnUiThread()方法返回主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText=response.body().string();
                boolean result=false;
                if ("province".equals(type)){
                    result= Utility.handleProvinceResponse(responseText);

                }else if ("city".equals(type)){
                    result=Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if ("county".equals(type)){
                    result=Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                if (result){
                    //因为需要对UI进行操作，所以需要通过runOnUiThread()方法返回主线程处理逻辑
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)){
                                queryProvinces();
                            }else if ("city".equals(type)){
                                queryCities();
                            }else if ("county".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }

            }
        });
    }
    /*
    * 显示对话进度条
    * */
    private void showProgressDialog(){
        if (progressDialog==null){
            progressDialog=new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);

        }
        progressDialog.show();
    }
    /*
    * 关闭进度条
    * */
    private void closeProgressDialog(){
        if (progressDialog!=null){
            progressDialog.dismiss();
        }
    }
}
