package net.oschina.app.ui;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.google.gson.reflect.TypeToken;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.TextHttpResponseHandler;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.net.AsyncWeiboRunner;
import com.sina.weibo.sdk.net.RequestListener;
import com.sina.weibo.sdk.net.WeiboParameters;
import com.tencent.mm.sdk.modelmsg.SendAuth;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import net.oschina.app.AppConfig;
import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.api.ApiHttpClient;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.base.BaseActivity;
import net.oschina.app.bean.Constants;
import net.oschina.app.bean.LoginUserBean;
import net.oschina.app.bean.OpenIdCatalog;
import net.oschina.app.improve.bean.UserV2;
import net.oschina.app.improve.bean.base.ResultBean;
import net.oschina.app.improve.share.constant.OpenConstant;
import net.oschina.app.util.CyptoUtils;
import net.oschina.app.util.DialogHelp;
import net.oschina.app.util.TDevice;
import net.oschina.app.util.XmlUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;

import butterknife.Bind;
import butterknife.OnClick;
import cz.msebera.android.httpclient.Header;

/**
 * 用户登录界面
 *
 * @author kymjs (http://www.kymjs.com/)
 */
public class LoginActivity extends BaseActivity implements IUiListener {

    public static final int REQUEST_CODE_INIT = 0;
    private static final String BUNDLE_KEY_REQUEST_CODE = "BUNDLE_KEY_REQUEST_CODE";
    protected static final String TAG = LoginActivity.class.getSimpleName();
    // private UMSocialService mController;

    @Bind(R.id.et_username)
    EditText mEtUserName;

    @Bind(R.id.et_password)
    EditText mEtPassword;

    private final int requestCode = REQUEST_CODE_INIT;
    private String mUserName = "";
    private String mPassword = "";

    private static final int LOGIN_TYPE_SINA = 1;
    private static final int LOGIN_TYPE_QQ = 2;
    private static final int LOGIN_TYPE_WX = 3;

    private int loginType;
    private AuthInfo authInfo;
    private SsoHandler ssoHandler;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_login;
    }

    @Override
    public void initView() {

    }

    @Override
    protected boolean hasBackButton() {
        return true;
    }

    @Override
    protected int getActionBarTitle() {
        return R.string.login;
    }

    @Override
    @OnClick({R.id.btn_login, R.id.iv_qq_login, R.id.iv_wx_login, R.id.iv_sina_login})
    public void onClick(View v) {

        int id = v.getId();
        switch (id) {
            case R.id.btn_login:
                handleLogin();
                break;
            case R.id.iv_qq_login:
                qqLogin();
                break;
            case R.id.iv_wx_login:
                wxLogin();
                break;
            case R.id.iv_sina_login:
                sinaLogin();
                break;
            default:
                break;
        }
    }

    private void handleLogin() {

        if (prepareForLogin()) {
            return;
        }

        // if the data has ready
        mUserName = mEtUserName.getText().toString();
        mPassword = mEtPassword.getText().toString();

        showWaitDialog(R.string.progress_login);
        OSChinaApi.login(mUserName, mPassword, mHandler);
    }

    private final AsyncHttpResponseHandler mHandler = new AsyncHttpResponseHandler() {

        @Override
        public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
            LoginUserBean loginUserBean = XmlUtils.toBean(LoginUserBean.class, arg2);
            if (loginUserBean != null) {
                handleLoginBean(loginUserBean, arg1);
            }
        }

        @Override
        public void onFailure(int arg0, Header[] arg1, byte[] arg2,
                              Throwable arg3) {
            AppContext.showToast("网络出错" + arg0);
        }

        @Override
        public void onFinish() {
            super.onFinish();
            hideWaitDialog();
        }
    };

    private void handleLoginSuccess() {
        Intent data = new Intent();
        data.putExtra(BUNDLE_KEY_REQUEST_CODE, requestCode);
        setResult(RESULT_OK, data);
        this.sendBroadcast(new Intent(Constants.INTENT_ACTION_USER_CHANGE));
        TDevice.hideSoftKeyboard(getWindow().getDecorView());

        OSChinaApi.getUserInfo(0,new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString,
                                  Throwable throwable) {

            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {

                try {
                    Type type = new TypeToken<ResultBean<UserV2>>() {
                    }.getType();

                    ResultBean resultBean = AppContext.createGson().fromJson(responseString, type);
                    if (resultBean.isSuccess()) {
                        UserV2 userInfo = (UserV2) resultBean.getResult();
                       // CacheManager.saveObject(LoginActivity.this, userInfo, NewUserInfoFragment.CACHE_NAME);
                        finish();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    onFailure(statusCode, headers, responseString, e);
                }

            }
        });

    }

    private boolean prepareForLogin() {
        if (!TDevice.hasInternet()) {
            AppContext.showToastShort(R.string.tip_no_internet);
            return true;
        }
        if (mEtUserName.length() == 0) {
            mEtUserName.setError("请输入邮箱/用户名");
            mEtUserName.requestFocus();
            return true;
        }

        if (mEtPassword.length() == 0) {
            mEtPassword.setError("请输入密码");
            mEtPassword.requestFocus();
            return true;
        }

        return false;
    }

    @Override
    public void initData() {

        mEtUserName.setText(AppContext.getInstance()
                .getProperty("user.account"));
        mEtPassword.setText(CyptoUtils.decode("oschinaApp", AppContext
                .getInstance().getProperty("user.pwd")));
    }

    /**
     * QQ登陆
     */
    private void qqLogin() {
        loginType = LOGIN_TYPE_QQ;
        Tencent mTencent = Tencent.createInstance(AppConfig.APP_QQ_KEY, this);
        mTencent.login(this, "all", this);
    }

    BroadcastReceiver receiver;

    /**
     * 微信登陆
     */
    private void wxLogin() {
        loginType = LOGIN_TYPE_WX;
        IWXAPI api = WXAPIFactory.createWXAPI(this, Constants.WEICHAT_APPID, false);
        api.registerApp(Constants.WEICHAT_APPID);

        if (!api.isWXAppInstalled()) {
            AppContext.showToast("手机中没有安装微信客户端");
            return;
        }
        // 唤起微信登录授权
        final SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        req.state = "wechat_login";
        api.sendReq(req);
        // 注册一个广播，监听微信的获取openid返回（类：WXEntryActivity中）
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(OpenIdCatalog.WECHAT);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String openid_info = intent.getStringExtra(LoginBindActivityChooseActivity
                            .BUNDLE_KEY_OPENIDINFO);
                    openIdLogin(OpenIdCatalog.WECHAT, openid_info);
                    // 注销这个监听广播
                    if (receiver != null) {
                        unregisterReceiver(receiver);
                    }
                }
            }
        };

        registerReceiver(receiver, intentFilter);
    }

    /**
     * 新浪登录
     */
    private void sinaLogin() {

        // 创建授权认证信息
        authInfo = new AuthInfo(this, OpenConstant.WB_APP_KEY, OpenConstant.REDIRECT_URL, null);

        ssoHandler = new SsoHandler(this, authInfo);

        ssoHandler.authorize(new WeiboAuthListener() {

                                 @Override
                                 public void onComplete(Bundle bundle) {

                                     final Oauth2AccessToken oauth2AccessToken =
                                             Oauth2AccessToken.parseAccessToken(bundle);

                                     if (oauth2AccessToken.isSessionValid()) {

                                         WeiboParameters parameters = new WeiboParameters(OpenConstant.WB_APP_KEY);
                                         parameters.put("uid", oauth2AccessToken.getUid());
                                         parameters.put("access_token", oauth2AccessToken.getToken());

                                         AsyncWeiboRunner asyncWeiboRunner = new AsyncWeiboRunner(LoginActivity.this);

                                         String url = "https://api.weibo.com/2/users/show.json";
                                         asyncWeiboRunner.requestAsync(url, parameters, "GET", new RequestListener() {
                                             @Override
                                             public void onComplete(String s) {

                                                 try {
                                                     JSONObject jsonObject = new JSONObject(s);

                                                     jsonObject.put("openid", jsonObject.getLong("id"));

                                                     String gender = jsonObject.getString("gender");
                                                     jsonObject.remove("gender");
                                                     if ("m".equals(gender)) {
                                                         jsonObject.put("gender", 1);
                                                     } else {
                                                         jsonObject.put("gender", 2);
                                                     }

                                                     jsonObject.put("access_token", oauth2AccessToken.getToken());

                                                     openIdLogin(OpenIdCatalog.WEIBO, jsonObject
                                                             .toString());

                                                 } catch (JSONException e) {
                                                     e.printStackTrace();
                                                 }


                                             }

                                             @Override
                                             public void onWeiboException(WeiboException e) {

                                             }
                                         });


                                     }

                                 }

                                 @Override
                                 public void onWeiboException(WeiboException e) {
                                     AppContext.showToast("新浪授权失败");
                                 }

                                 @Override
                                 public void onCancel() {
                                     AppContext.showToast("已取消新浪登陆");
                                 }
                             }

        );

        // if (mController == null)
        // mController = UMServiceFactory.getUMSocialService("com.umeng.login");
        loginType = LOGIN_TYPE_SINA;
        // SinaSsoHandler sinaSsoHandler = new SinaSsoHandler();
        //mController.getConfig().setSsoHandler(sinaSsoHandler);
        // mController.doOauthVerify(this, SHARE_MEDIA.SINA,
//                new SocializeListeners.UMAuthListener() {
//
//                    @Override
//                    public void onStart(SHARE_MEDIA arg0) {
//                    }
//
//                    @Override
//                    public void onError(SocializeException arg0,
//                                        SHARE_MEDIA arg1) {
//                        AppContext.showToast("新浪授权失败");
//                    }
//
//                    @Override
//                    public void onComplete(Bundle value, SHARE_MEDIA arg1) {
//                        if (value != null && !TextUtils.isEmpty(value.getString("uid"))) {
//                            // 获取平台信息
//                            mController.getPlatformInfo(LoginActivity.this, SHARE_MEDIA.SINA,
// new SocializeListeners.UMDataListener() {
//                                @Override
//                                public void onStart() {
//
//                                }
//
//                                @Override
//                                public void onComplete(int i, Map<String, Object> map) {
//                                    if (i == 200 && map != null) {
//                                        StringBuilder sb = new StringBuilder("{");
//                                        Set<String> keys = map.keySet();
//                                        int index = 0;
//                                        for (String key : keys) {
//                                            index++;
//                                            String jsonKey = key;
//                                            if (jsonKey.equals("uid")) {
//                                                jsonKey = "openid";
//                                            }
//                                            sb.append(String.format("\"%s\":\"%s\"", jsonKey,
// map.get(key).toString()));
//                                            if (index != map.size()) {
//                                                sb.append(",");
//                                            }
//                                        }
//                                        sb.append("}");
//                                        openIdLogin(OpenIdCatalog.WEIBO, sb.toString());
//                                    } else {
//                                        AppContext.showToast("发生错误：" + i);
//                                    }
//                                }
//                            });
//                        } else {
//                            AppContext.showToast("授权失败");
//                        }
//                    }
//
//                    @Override
//                    public void onCancel(SHARE_MEDIA arg0) {
//                        AppContext.showToast("已取消新浪登陆");
//                    }
//                });
    }

    // 获取到QQ授权登陆的信息
    @Override
    public void onComplete(Object o) {
        openIdLogin(OpenIdCatalog.QQ, o.toString());
    }

    @Override
    public void onError(UiError uiError) {

    }

    @Override
    public void onCancel() {

    }

    /***
     * @param catalog    第三方登录的类别
     * @param openIdInfo 第三方的信息
     */
    private void openIdLogin(final String catalog, final String openIdInfo) {
        final ProgressDialog waitDialog = DialogHelp.getWaitDialog(this, "登陆中...");
        OSChinaApi.open_login(catalog, openIdInfo, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                LoginUserBean loginUserBean = XmlUtils.toBean(LoginUserBean.class, responseBody);
                if (loginUserBean.getResult().OK()) {
                    handleLoginBean(loginUserBean, headers);
                } else {
                    // 前往绑定或者注册操作
                    Intent intent = new Intent(LoginActivity.this,
                            LoginBindActivityChooseActivity.class);
                    intent.putExtra(LoginBindActivityChooseActivity.BUNDLE_KEY_CATALOG, catalog);
                    intent.putExtra(LoginBindActivityChooseActivity.BUNDLE_KEY_OPENIDINFO,
                            openIdInfo);
                    startActivityForResult(intent, REQUEST_CODE_OPENID);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                AppContext.showToast("网络出错" + statusCode);
            }

            @Override
            public void onStart() {
                super.onStart();
                waitDialog.show();
            }

            @Override
            public void onFinish() {
                super.onFinish();
                waitDialog.dismiss();
            }
        });
    }

    public static final int REQUEST_CODE_OPENID = 1000;
    // 登陆实体类
    public static final String BUNDLE_KEY_LOGINBEAN = "bundle_key_loginbean";

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


        if (loginType == LOGIN_TYPE_QQ) {
            Tencent tencent = Tencent.createInstance(AppConfig.APP_QQ_KEY, this);
            //在某些低端机上调用登录后，由于内存紧张导致APP被系统回收，登录成功后无法成功回传数据。
            // 解决办法
            tencent.handleLoginData(data, this);
        }

        super.onActivityResult(requestCode, resultCode, data);
        if (loginType == LOGIN_TYPE_SINA) {
//            UMSsoHandler ssoHandler = mController.getConfig().getSsoHandler(requestCode);
//            if (ssoHandler != null) {
//                ssoHandler.authorizeCallBack(requestCode, resultCode, data);
//            }

            // SSO 授权回调
            // 重要：发起 SSO 登陆的 Activity 必须重写 onActivityResults
            if (authInfo == null)
                authInfo = new AuthInfo(this, OpenConstant.WB_APP_KEY, OpenConstant.REDIRECT_URL, "all");
            if (ssoHandler == null)
                ssoHandler = new SsoHandler(this, authInfo);
            ssoHandler.authorizeCallBack(requestCode, resultCode, data);


        } else {

            switch (requestCode) {
                case REQUEST_CODE_OPENID:
                    if (data == null) {
                        return;
                    }
                    LoginUserBean loginUserBean = (LoginUserBean) data.getSerializableExtra
                            (BUNDLE_KEY_LOGINBEAN);
                    if (loginUserBean != null) {
                        handleLoginBean(loginUserBean, null);
                    }
                    break;
                default:
                    break;
            }
        }

    }


    // 处理loginBean
    @SuppressWarnings("deprecation")
    private void handleLoginBean(LoginUserBean loginUserBean, Header[] headers) {
        if (loginUserBean.getResult().OK()) {
            // 更新相关Cookie信息
            ApiHttpClient.updateCookie(ApiHttpClient.getHttpClient(), headers);

            // 保存用户信息
            loginUserBean.getUser().setAccount(mUserName);
            loginUserBean.getUser().setPwd(mPassword);
            loginUserBean.getUser().setRememberMe(true);
            AppContext.getInstance().saveUserInfo(loginUserBean.getUser());

            // 成功回调
            hideWaitDialog();
            handleLoginSuccess();
        } else {
            AppContext.getInstance().cleanLoginInfo();
            AppContext.showToast(loginUserBean.getResult().getErrorMessage());
        }
    }
}
