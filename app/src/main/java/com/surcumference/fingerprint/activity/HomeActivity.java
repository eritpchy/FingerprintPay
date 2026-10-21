package com.surcumference.fingerprint.activity;


import static com.surcumference.fingerprint.Constant.HELP_URL_ALIPAY;
import static com.surcumference.fingerprint.Constant.HELP_URL_FAQ;
import static com.surcumference.fingerprint.Constant.HELP_URL_LICENSE;
import static com.surcumference.fingerprint.Constant.HELP_URL_QQ;
import static com.surcumference.fingerprint.Constant.HELP_URL_TAOBAO;
import static com.surcumference.fingerprint.Constant.HELP_URL_UNIONPAY;
import static com.surcumference.fingerprint.Constant.HELP_URL_WECHAT;
import static com.surcumference.fingerprint.Constant.PROJECT_URL;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.adapter.PreferenceAdapter;
import com.surcumference.fingerprint.network.update.UpdateFactory;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.Umeng;
import com.surcumference.fingerprint.util.bugfixer.TagManagerBugFixer;
import com.surcumference.fingerprint.view.DonateView;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {


    private PreferenceAdapter mListAdapter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Umeng.init(this);
        setContentView(R.layout.home);

        ListView listView = (ListView) findViewById(R.id.list);
        List<PreferenceAdapter.Data> list = new ArrayList<>();
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_wechat), Lang.getString(R.id.settings_sub_title_help_wechat)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_alipay), Lang.getString(R.id.settings_sub_title_help_alipay)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_taobao), Lang.getString(R.id.settings_sub_title_help_taobao)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_qq), Lang.getString(R.id.settings_sub_title_help_qq)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_unionpay), Lang.getString(R.id.settings_sub_title_help_unionpay)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_help_faq), Lang.getString(R.id.settings_sub_title_help_faq)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_qq_group), Lang.getString(R.id.settings_sub_title_qq_group)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_checkupdate), Lang.getString(R.id.settings_sub_title_checkupdate)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_license), Lang.getString(R.id.settings_sub_title_license)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_webside), PROJECT_URL));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_donate), Lang.getString(R.id.settings_sub_title_donate)));
        list.add(new PreferenceAdapter.Data(Lang.getString(R.id.settings_title_version), BuildConfig.VERSION_NAME));
        mListAdapter = new PreferenceAdapter(list);
        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener(this);
        Task.onMain(1000L, () -> UpdateFactory.doUpdateCheck(HomeActivity.this));
        TagManagerBugFixer.fix();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        PreferenceAdapter.Data data = mListAdapter.getItem(i);
        if (data == null || TextUtils.isEmpty(data.title)) {
            return;
        }
        if (Lang.getString(R.id.settings_title_help_wechat).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_WECHAT);
        } else if (Lang.getString(R.id.settings_title_help_alipay).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_ALIPAY);
        } else if (Lang.getString(R.id.settings_title_help_taobao).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_TAOBAO);
        } else if (Lang.getString(R.id.settings_title_help_qq).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_QQ);
        } else if (Lang.getString(R.id.settings_title_help_unionpay).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_UNIONPAY);
        } else if (Lang.getString(R.id.settings_title_help_faq).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_FAQ);
        } else if (Lang.getString(R.id.settings_title_qq_group).equals(data.title)) {
            joinQQGroup();
        } else if (Lang.getString(R.id.settings_title_donate).equals(data.title)) {
            new DonateView(this).showInDialog();
        } else if (Lang.getString(R.id.settings_title_checkupdate).equals(data.title)) {
            UpdateFactory.doUpdateCheck(this, false, true);
        } else if (Lang.getString(R.id.settings_title_license).equals(data.title)) {
            WebActivity.openUrl(this, HELP_URL_LICENSE);
        } else if (Lang.getString(R.id.settings_title_webside).equals(data.title)) {
            com.surcumference.fingerprint.util.UrlUtils.openUrl(this, PROJECT_URL);
            Toaster.showLong(Lang.getString(R.id.toast_give_me_star));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Umeng.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Umeng.onPause(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_settings) {
            SettingsActivity.open(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean joinQQGroup() {
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + "A2WjHt6jDpAraj7z4LfTsSbS9SkZVEXi"));
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}

