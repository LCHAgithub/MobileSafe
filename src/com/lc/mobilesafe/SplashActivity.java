package com.lc.mobilesafe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import net.tsz.afinal.FinalHttp;
import net.tsz.afinal.http.AjaxCallBack;

import org.json.JSONException;
import org.json.JSONObject;

import com.lc.mobilesafe.activity.HomeActivity;
import com.lc.mobilesafe.utils.StreamTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SplashActivity extends Activity {

	protected static final String TAG = "SplashActivity";
	protected static final int ENTER_HOME = 1;
	protected static final int SHOW_UPDATE_DIALOG = 2;
	protected static final int URL_ERROR = 3;
	protected static final int NETWORK_ERROR = 4;
	protected static final int JSON_ERROR = 5;
	private TextView tv_splash_version;
	private TextView tv_splash_update;
	private String apkurl;
	private String description;
	private SharedPreferences sp;
	Handler handler = new Handler(){
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case ENTER_HOME:
				//进入主界面
				enterHome();
				break;
			case SHOW_UPDATE_DIALOG:
				 showUpdate();
				break;
			case URL_ERROR:
				enterHome();
				Toast.makeText(SplashActivity.this, "URL异常", Toast.LENGTH_SHORT).show();
				break;
			case NETWORK_ERROR:
				enterHome();
				Toast.makeText(SplashActivity.this, "网络异常", Toast.LENGTH_SHORT).show();
				break;
			case JSON_ERROR:
				enterHome();
				Toast.makeText(SplashActivity.this, "JSON异常", Toast.LENGTH_SHORT).show();
				break;

			}
		};
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sp = getSharedPreferences("config", MODE_PRIVATE);
		setContentView(R.layout.activity_splash);
		tv_splash_version = (TextView) findViewById(R.id.tv_splash_version);
		tv_splash_version.setText("版本号:" + getVersion());
		tv_splash_update = (TextView) findViewById(R.id.tv_splash_update);
		
		if (sp.getBoolean("update", false)) {
			checkVersion();
		}else {
			//延迟两秒进入主页面
			handler.postDelayed(new Runnable() {
				public void run() {
					enterHome();
				}
			}, 2000);
		}
		AlphaAnimation aa = new AlphaAnimation(0.1f, 1.0f);
		aa.setDuration(3000);
		findViewById(R.id.rl_splash_root).startAnimation(aa);
	}
	
	protected void showUpdate() {
		AlertDialog.Builder builder = new Builder(this);
		builder.setTitle("要升级吗");
		builder.setMessage(description);
		//取消监听
		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				dialog.dismiss();
				enterHome();
			}
		});
		builder.setNegativeButton("下次再说", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//消除对话框
				dialog.dismiss();
				enterHome();
			}
		});
		builder.setPositiveButton("立即更新", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//下载更新
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					FinalHttp fh = new FinalHttp();
					fh.download(apkurl, Environment.getExternalStorageDirectory()+"/mobilesafe2.0.apk", new AjaxCallBack<File>() {

						@Override
						public void onFailure(Throwable t, int errorNo,
								String strMsg) {
							super.onFailure(t, errorNo, strMsg);
							t.printStackTrace();
							Toast.makeText(SplashActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onLoading(long count, long current) {
							super.onLoading(count, current);
							tv_splash_update.setVisibility(View.VISIBLE);
							int progress = (int) (current * 100 / count);
							tv_splash_update.setText("下载进度:"+progress+"%");
						}

						@Override
						public void onSuccess(File t) {
							super.onSuccess(t);
							Toast.makeText(SplashActivity.this, "下载成功", Toast.LENGTH_SHORT).show();
							//安装应用
							Intent intent = new Intent();
							intent.setAction("android.intent.action.VIEW");
							intent.setDataAndType(Uri.fromFile(t), "application/vnd.android.package-archive");
							startActivity(intent);
						}
					});
				}
			}
		});
		builder.show();
	}
/**
 * 进入主页面
 */
	protected void enterHome() {
		Intent intent = new Intent(this, HomeActivity.class);
		startActivity(intent);
		finish();
	}

	/**
	 * 检查版本更新
	 * 
	 * @throws IOException
	 */
	private void checkVersion() {
		new Thread() {
			public void run() {
				Message msg = Message.obtain();
				long startTime = System.currentTimeMillis();
				try {
					URL url = new URL(getString(R.string.serverurl));
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(4000);
					if (200 == conn.getResponseCode()) {
						InputStream is = conn.getInputStream();
						String result = StreamTools.readFromStream(is);
						Log.e(TAG, "result==" + result);
						//解析json
						JSONObject obj = new JSONObject(result);
						String version = (String) obj.get("version");
						description = (String)obj.getString("description");
						apkurl = (String) obj.get("apkurl");
						
						if (getVersion().equals(version)) {
							//没有新版本,进入主页面
							msg.what = ENTER_HOME;
						}else {
							//弹出升级对话框
							msg.what = SHOW_UPDATE_DIALOG;
						}
					}
				} catch (MalformedURLException e) {
					// 地址错误
					e.printStackTrace();
					msg.what = URL_ERROR;
				} catch (IOException e) {
					// 网络连接异常
					e.printStackTrace();
					msg.what = NETWORK_ERROR;
				} catch (JSONException e) {
					//解析json异常
					e.printStackTrace();
					msg.what = JSON_ERROR;
				}finally{
					long endTime = System.currentTimeMillis();
					long dTime = endTime - startTime;
					if (dTime < 2000) {
						SystemClock.sleep(2000 - dTime);
					}
					handler.sendMessage(msg);
				}
			};
		}.start();
	}

	/**
	 * 获取版本号
	 */
	private String getVersion() {
		PackageManager pm = getPackageManager();
		try {
			PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
			return packageInfo.versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return "";
		}
	}
}
