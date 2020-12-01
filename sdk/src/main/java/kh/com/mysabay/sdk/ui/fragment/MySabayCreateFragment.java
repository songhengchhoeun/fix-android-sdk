package kh.com.mysabay.sdk.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.AppCompatEditText;
import android.view.View;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import kh.com.mysabay.sdk.R;
import kh.com.mysabay.sdk.base.BaseFragment;
import kh.com.mysabay.sdk.databinding.FmCreateMysabayBinding;
import kh.com.mysabay.sdk.pojo.NetworkState;
import kh.com.mysabay.sdk.ui.activity.LoginActivity;
import kh.com.mysabay.sdk.utils.MessageUtil;
import kh.com.mysabay.sdk.viewmodel.UserApiVM;

public class MySabayCreateFragment extends BaseFragment<FmCreateMysabayBinding, UserApiVM> {

    public static final String TAG = MySabayCreateFragment.class.getSimpleName();

    @NotNull
    @Contract(" -> new")
    public static MySabayCreateFragment newInstance() {
        return new MySabayCreateFragment();
    }

    @Override
    public int getLayoutId() {
        return R.layout.fm_create_mysabay;
    }

    @Override
    public void initializeObjects(View v, Bundle args) {
        this.viewModel = LoginActivity.loginActivity.viewModel;
    }

    @Override
    public void assignValues() {
        new Handler().postDelayed(() -> showProgressState(new NetworkState(NetworkState.Status.SUCCESS)), 500);
    }

    @Override
    public void addListeners() {
        viewModel.liveNetworkState.observe(this, this::showProgressState);

        mViewBinding.btnClose.setOnClickListener(v -> {
            if (v.getContext() instanceof LoginActivity)
                getActivity().finish();
        });

        mViewBinding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null)
                getActivity().onBackPressed();
        });
        mViewBinding.btnCreateMysabay.setOnClickListener(v -> {
            String username = mViewBinding.edtUsername.getText().toString();
            String password = mViewBinding.edtPassword.getText().toString();
            String confirmPassword = mViewBinding.edtConfirmPassword.getText().toString();
            if (StringUtils.isAnyBlank(username)) {
                showCheckFields(mViewBinding.edtUsername, R.string.msg_input_username);
            } else if (StringUtils.isAnyBlank(password)) {
                showCheckFields(mViewBinding.edtPassword, R.string.msg_input_password);
            } else if (StringUtils.isAnyBlank(confirmPassword)) {
                showCheckFields(mViewBinding.edtConfirmPassword, R.string.msg_input_confirm_password);
            } else if (!StringUtils.isAnyBlank(confirmPassword)) {
                if (!password.equals(confirmPassword)) {
                    showCheckFields(mViewBinding.edtConfirmPassword, R.string.msg_confirm_password_not_match);
                }
            }
            else {
                viewModel.postToLoginMySabayWithGraphql(v.getContext(), username, password);
            }
        });
    }

    private void showCheckFields(AppCompatEditText view, int msg) {
        if (view != null) {
            YoYo.with(Techniques.Shake).duration(600).playOn(view);
            view.requestFocus();
        }
        MessageUtil.displayToast(getContext(), getString(msg));
    }

    @Override
    public View assignProgressView() {
        return mViewBinding.viewEmpty.progressBar;
    }

    @Override
    public View assignEmptyView() {
        return mViewBinding.viewEmpty.viewRetry;
    }

    @Override
    protected Class<UserApiVM> getViewModel() {
        return null;
    }

    @Override
    protected void onOnlineCallback() {

    }
}
