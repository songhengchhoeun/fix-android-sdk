package kh.com.mysabay.sdk.viewmodel;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.exception.ApolloNetworkException;
import com.apollographql.apollo.request.RequestHeaders;
import com.google.gson.Gson;
import com.mysabay.sdk.Checkout_getPaymentServiceProviderForProductQuery;
import com.mysabay.sdk.CreateInvoiceMutation;
import com.mysabay.sdk.GetExchangeRateQuery;
import com.mysabay.sdk.GetInvoiceByIdQuery;
import com.mysabay.sdk.GetPaymentDetailQuery;
import com.mysabay.sdk.GetProductsByServiceCodeQuery;
import com.mysabay.sdk.type.Invoice_CreateInvoiceInput;
import com.mysabay.sdk.type.Store_PagerInput;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.matomo.sdk.extra.EcommerceItems;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import kh.com.mysabay.sdk.Globals;
import kh.com.mysabay.sdk.MySabaySDK;
import kh.com.mysabay.sdk.R;
import kh.com.mysabay.sdk.SdkConfiguration;
import kh.com.mysabay.sdk.callback.DataCallback;
import kh.com.mysabay.sdk.pojo.AppItem;
import kh.com.mysabay.sdk.pojo.NetworkState;
import kh.com.mysabay.sdk.pojo.TrackingOrder.TrackingOrder;
import kh.com.mysabay.sdk.pojo.googleVerify.GoogleVerifyBody;
import kh.com.mysabay.sdk.pojo.googleVerify.GoogleVerifyResponse;
import kh.com.mysabay.sdk.pojo.invoice.InvoiceItemResponse;
import kh.com.mysabay.sdk.pojo.mysabay.MySabayItemResponse;
import kh.com.mysabay.sdk.pojo.mysabay.ProviderResponse;
import kh.com.mysabay.sdk.pojo.payment.PaymentResponseItem;
import kh.com.mysabay.sdk.pojo.payment.SubscribePayment;
import kh.com.mysabay.sdk.pojo.shop.ShopItem;
import kh.com.mysabay.sdk.pojo.thirdParty.payment.Data;
import kh.com.mysabay.sdk.repository.StoreRepo;
import kh.com.mysabay.sdk.ui.activity.StoreActivity;
import kh.com.mysabay.sdk.utils.AppRxSchedulers;
import kh.com.mysabay.sdk.utils.LogUtil;
import kh.com.mysabay.sdk.utils.MessageUtil;
import kh.com.mysabay.sdk.webservice.AbstractDisposableObs;
import kh.com.mysabay.sdk.webservice.Constant;

/**
 * Created by Tan Phirum on 3/8/20
 * Gmail phirumtan@gmail.com
 */
public class StoreApiVM extends ViewModel {

    private static final String TAG = StoreApiVM.class.getSimpleName();

    private final StoreRepo storeRepo;
    private final SdkConfiguration sdkConfiguration;

    ApolloClient apolloClient;

    @Inject
    AppRxSchedulers appRxSchedulers;
    @Inject
    Gson gson;

    private final MediatorLiveData<String> _msgError = new MediatorLiveData<>();
    private final MediatorLiveData<NetworkState> _networkState;
    private final MediatorLiveData<List<ShopItem>> _shopItem;
    private final CompositeDisposable mCompos;
    private final MediatorLiveData<ShopItem> mDataSelected;
    private final MediatorLiveData<List<MySabayItemResponse>> mySabayItemMediatorLiveData;
    public final MediatorLiveData<List<ProviderResponse>> _thirdPartyItemMediatorLiveData;

    @Inject
    public StoreApiVM(ApolloClient apolloClient, StoreRepo storeRepo) {
        this.apolloClient = apolloClient;
        this.storeRepo = storeRepo;
        this._networkState = new MediatorLiveData<>();
        this._shopItem = new MediatorLiveData<>();
        this.mCompos = new CompositeDisposable();
        this.mDataSelected = new MediatorLiveData<>();
        this.mySabayItemMediatorLiveData = new MediatorLiveData<>();
        this._thirdPartyItemMediatorLiveData = new MediatorLiveData<>();
        this.sdkConfiguration = MySabaySDK.getInstance().getSdkConfiguration();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mCompos != null) {
            mCompos.dispose();
            mCompos.clear();
        }
    }

    public void getShopFromServerGraphQL(@NotNull Context context) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        _networkState.setValue(new NetworkState(NetworkState.Status.LOADING));
        Store_PagerInput pager = Store_PagerInput.builder().page(1).limit(20).build();
        List<ShopItem> shopItems = new ArrayList<ShopItem>();
        apolloClient.query(new GetProductsByServiceCodeQuery(MySabaySDK.getInstance().serviceCode(), new Input<>(pager, true))).toBuilder()
                .requestHeaders(RequestHeaders.builder()
                .addHeader("Authorization", "Bearer " + appItem.token).build())
                .build()
                .enqueue(new ApolloCall.Callback<GetProductsByServiceCodeQuery.Data>() {
                    @Override
                    public void onResponse(@NotNull Response<GetProductsByServiceCodeQuery.Data> response) {
                        if (response.getErrors() != null) {
                            showErrorMsg(context, "Get product failed");
                        } else {
                            if (response.getData() != null) {
                                List<GetProductsByServiceCodeQuery.Product> products =  response.getData().store_listProduct().products();
                                for (GetProductsByServiceCodeQuery.Product product: products) {
                                    ShopItem shopItem = gson.fromJson(gson.toJson(product), ShopItem.class);
                                    shopItems.add(shopItem);
                                }
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        _shopItem.setValue(shopItems);
                                        _networkState.setValue(new NetworkState(NetworkState.Status.SUCCESS));
                                        MySabaySDK.getInstance().trackEvents(context, "sdk-" + Constant.store, Constant.process, "get-store-success");
                                    }
                                });
                            } else {
                                showErrorMsg(context, "Data is empty");
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NotNull ApolloException e) {
                        showErrorMsg(e, context, "Get shop failed");
                        MySabaySDK.getInstance().trackEvents(context, "sdk-" + Constant.store, Constant.process, "get-store-failed");
                    }
                });
    }

    public LiveData<List<ShopItem>> getShopItem() {
        return _shopItem;
    }

    public LiveData<NetworkState> getNetworkState() {
        return _networkState;
    }

    public LiveData<List<ProviderResponse>> getThirdPartyProviders() {
        return _thirdPartyItemMediatorLiveData;
    }

    public LiveData<List<MySabayItemResponse>> getMySabayProvider() {
        return mySabayItemMediatorLiveData;
    }

    public void setShopItemSelected(ShopItem data) {
        _networkState.setValue(new NetworkState(NetworkState.Status.SUCCESS));
        this.mDataSelected.setValue(data);
    }

    public LiveData<ShopItem> getItemSelected() {
        return this.mDataSelected;
    }

    /**
     * @param context
     * @param itemId
     */
    public void  getMySabayCheckoutWithGraphQL(@NotNull Context context, String itemId) {
        _networkState.setValue(new NetworkState(NetworkState.Status.LOADING));
        apolloClient.query(new Checkout_getPaymentServiceProviderForProductQuery(itemId)).enqueue(new ApolloCall.Callback<Checkout_getPaymentServiceProviderForProductQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<Checkout_getPaymentServiceProviderForProductQuery.Data> response) {
                if (response.getErrors() != null) {
                    showErrorMsg(context, "Get Mysabay checkout failed");
                } else {
                    if (response.getData() != null) {
                       if (response.getData().checkout_getPaymentServiceProviderForProduct().paymentServiceProviders() != null) {
                            List<Checkout_getPaymentServiceProviderForProductQuery.PaymentServiceProvider> providers = response.getData().checkout_getPaymentServiceProviderForProduct().paymentServiceProviders();
                            List<MySabayItemResponse> mySabayItemResponses = new ArrayList<>();
                            for (Checkout_getPaymentServiceProviderForProductQuery.PaymentServiceProvider payment : providers) {
                                MySabayItemResponse paymentProvider = gson.fromJson(gson.toJson(payment), MySabayItemResponse.class);
                                mySabayItemResponses.add(paymentProvider);
                            }

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        mySabayItemMediatorLiveData.setValue(mySabayItemResponses);
                                    } catch (Exception e) {
                                        MessageUtil.displayToast(context, "Get mysabay checkout failed");
                                    }
                                    _networkState.setValue(new NetworkState(NetworkState.Status.SUCCESS));
                                }
                            });

                       } else {
                           showErrorMsg(context, "Data is empty");
                       }
                    } else {
                        showErrorMsg(context, context.getString(R.string.msg_can_not_connect_server));
                    }
                }
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                showErrorMsg(e, context, "Get payment service provider failed");
            }
        });
    }

    /**
     * @param context
     * @param shopItem
     * @param provider
     * @param type
     * @param exChangeRate
     */
    public void createPayment(Context context, ShopItem shopItem, ProviderResponse provider, String type, double exChangeRate, DataCallback<Data> callback) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);

        List<Object> items = new ArrayList<>();
        JSONObject jsonObject=new JSONObject();
        JSONParser parser = new JSONParser();
        try {
            jsonObject.put("package_id",provider.packageId);
            jsonObject.put("displayName", shopItem.properties.displayName);
            jsonObject.put("packageCode", shopItem.properties.packageCode);
            items.add(parser.parse(jsonObject.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        double amount = shopItem.salePrice * exChangeRate;
        double priceOfItem = shopItem.salePrice;

        if (type.equals(Globals.MY_SABAY_PROVIDER)) {
            if (provider.issueCurrencies.size() != 0) {
                priceOfItem = Math.ceil(amount/100);
            }
        }

        Invoice_CreateInvoiceInput obj = Invoice_CreateInvoiceInput.builder()
                .items(items)
                .amount(priceOfItem)
                .currency(provider.issueCurrencies.get(0))
                .notes("this is invoice")
                .ssnTxHash("")
                .paymentProvider("")
                .build();
        Input<Invoice_CreateInvoiceInput> input = Input.fromNullable(obj);

        _networkState.setValue(new NetworkState(NetworkState.Status.LOADING));
        apolloClient.mutate(new CreateInvoiceMutation(input)).toBuilder()
                .requestHeaders(RequestHeaders.builder().addHeader("Authorization", "Bearer " + appItem.token).build())
                .build()
                .enqueue(new ApolloCall.Callback<CreateInvoiceMutation.Data>() {
                    @Override
                    public void onResponse(@NotNull Response<CreateInvoiceMutation.Data> response) {
                        if (response.getErrors() != null) {
                            showErrorMsg(context, "Create invoice failed");
                        } else {
                            if(response.getData() != null) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        getPaymentDetail((StoreActivity) context, shopItem, provider.id, response.getData().invoice_createInvoice().invoice().id(), callback);
                                    }
                                });
                            } else {
                                showErrorMsg(context, "Invoice is empty");
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NotNull ApolloException e) {
                        showErrorMsg(e, context, "Create invoice failed");
                    }
                });
    }

    /**
     * @param context
     * @param shopItem
     * @param id
     * @param invoiceId
     * @param callback
     */
    public void getPaymentDetail(StoreActivity context, ShopItem shopItem, String id, String invoiceId, DataCallback<Data> callback) {
        String paymentAddress = MySabaySDK.getInstance().getPaymentAddress(invoiceId);
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);

        apolloClient.query(new GetPaymentDetailQuery(id, paymentAddress)).toBuilder()
                .requestHeaders(RequestHeaders.builder().addHeader("Authorization", "Bearer " + appItem.token).build())
                .build()
                .enqueue(new ApolloCall.Callback<GetPaymentDetailQuery.Data>() {
                    @Override
                    public void onResponse(@NotNull Response<GetPaymentDetailQuery.Data> response) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (response.getErrors() != null) {
                                    showErrorMsg(context, response.getErrors().get(0).getMessage());
                                } else {
                                    if (response.getData().checkout_getPaymentServiceProviderDetailForPayment() != null) {
                                        GetPaymentDetailQuery.Checkout_getPaymentServiceProviderDetailForPayment payment = response.getData().checkout_getPaymentServiceProviderDetailForPayment();
                                        Data data = new Data();
                                        data.withHash(payment.hash());
                                        data.withSignature(payment.signature());
                                        data.withPublicKey(payment.publicKey());
                                        data.withRequestUrl(payment.requestUrl());
                                        data.withAdditionalBody(payment.additionalBody());
                                        data.withAdditionalHeader(payment.additionalHeader());

                                        data.withPaymentAddress(paymentAddress);
                                        data.withInvoiceId(invoiceId);

                                        TrackingOrder trackingOrder = new TrackingOrder();
                                        EcommerceItems items = new EcommerceItems();
                                        items.addItem(new EcommerceItems.Item("sku").name(shopItem.properties.displayName).category("category").price((int) (shopItem.salePrice * 100)).quantity(1));

                                        trackingOrder.withEcommerceItems(items);
                                        trackingOrder.withDiscount(0);
                                        trackingOrder.withOrderId(data.invoiceId);
                                        trackingOrder.withGrandTotal((int) (shopItem.salePrice * 100));
                                        trackingOrder.withTax(0);
                                        trackingOrder.withSubTotal((int) (shopItem.salePrice * 100));
                                        trackingOrder.withShipping(0);

                                        MySabaySDK.getInstance().trackOrder(context, trackingOrder);
                                        callback.onSuccess(data);
                                    } else {
                                        showErrorMsg(context, "Get Payment Detail is error");
                                    }
                                }
                            }
                        });
                    }
                    @Override
                    public void onFailure(@NotNull ApolloException e) {
                        showErrorMsg(e, context, "Get payment Detail failed");
                    }
                });
    }

    public void postToVerifyAppInPurchase(Context context, Data data, String paymentAddress, GoogleVerifyBody body) {
        _networkState.setValue(new NetworkState(NetworkState.Status.LOADING));
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        mCompos.add(storeRepo.postToVerifyGoogle(data.requestUrl + paymentAddress, appItem.token, body)
                .subscribeOn(appRxSchedulers.io())
                .observeOn(appRxSchedulers.mainThread()).subscribe(new Consumer<GoogleVerifyResponse>() {
                    @Override
                    public void accept(GoogleVerifyResponse googleVerifyResponse) throws Exception {
                        _networkState.setValue(new NetworkState(NetworkState.Status.SUCCESS));
                        getInvoiceById(context, data.invoiceId, body);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        _networkState.setValue(new NetworkState(NetworkState.Status.ERROR));
                        MessageUtil.displayDialog(context, "Error" + throwable.getMessage());
                    }
                }));
    }

    /**
     * This method is use to buy item with mysabay payment
     */
    public void postToPaidWithMySabayProvider(Context context, Data data, String paymentAddress) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        if (getMySabayProvider().getValue() == null) return;

        storeRepo.postToPaid(data.requestUrl + paymentAddress, appItem.token, data.hash, data.signature, data.publicKey, paymentAddress).subscribeOn(appRxSchedulers.io())
                .observeOn(appRxSchedulers.mainThread())
                .subscribe(new AbstractDisposableObs<PaymentResponseItem>(context, _networkState) {
                    @Override
                    protected void onSuccess(PaymentResponseItem item) {
                        _networkState.setValue(new NetworkState(NetworkState.Status.SUCCESS));
                        getInvoiceById(context, data.invoiceId, item);
                    }

                    @Override
                    protected void onErrors(Throwable error) {
                        LogUtil.info("Error", error.getMessage());
                        _networkState.setValue(new NetworkState(NetworkState.Status.ERROR, "Payment with MySabay failed"));
                        EventBus.getDefault().post(new SubscribePayment(Globals.MY_SABAY, null, error.getMessage()));
                    }
                });
    }

    public void scheduledCheckPaymentStatus(Context context, Handler handler, String invoiceId, long interval, long repeat, DataCallback<InvoiceItemResponse> callback) {
        final int[] totalDelay = {0};
        try {
            handler.postDelayed(new Runnable() {
                public void run() {
                    totalDelay[0]++;
                    if(totalDelay[0] < (interval/repeat)) {
                        getInvoiceById(context, invoiceId, callback);
                        handler.postDelayed(this, repeat);
                    } else {
                        handler.removeCallbacks(this);
                    }
                }
            }, repeat);
        }catch (Exception e) {
           LogUtil.info("Error", e.getMessage());
        }
    }

    public void getInvoiceById(Context context, String id, Object item) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        if (id != null) {
            apolloClient.query(new GetInvoiceByIdQuery(id)).toBuilder()
                    .requestHeaders(RequestHeaders.builder().addHeader("Authorization", "Bearer " + appItem.token).build())
                    .build().enqueue(new ApolloCall.Callback<GetInvoiceByIdQuery.Data>() {
                @Override
                public void onResponse(@NotNull Response<GetInvoiceByIdQuery.Data> response) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (response.getErrors() != null) {
                                showErrorMsg(context, response.getErrors().get(0).getMessage());
                            } else {
                                if (!StringUtils.isEmpty(response.getData().invoice_getInvoiceById().ssnTxHash())) {
                                    EventBus.getDefault().post(new SubscribePayment(Globals.MY_SABAY, item, null));
                                    ((Activity) context).finish();
                                } else {
                                    showErrorMsg(context, context.getString(R.string.sorry_we_were_unable_to_process_your_payment));
                                }
                            }
                        }
                    });
                }

                @Override
                public void onFailure(@NotNull ApolloException e) {
                    showErrorMsg(e, context, e.getMessage());
                }
            });
        }
    }

    public void getInvoiceById(Context context, String id, DataCallback<InvoiceItemResponse> callback) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        if (id != null) {
            apolloClient.query(new GetInvoiceByIdQuery(id)).toBuilder()
                    .requestHeaders(RequestHeaders.builder().addHeader("Authorization", "Bearer " + appItem.token).build())
                    .build().enqueue(new ApolloCall.Callback<GetInvoiceByIdQuery.Data>() {
                @Override
                public void onResponse(@NotNull Response<GetInvoiceByIdQuery.Data> response) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (response.getErrors() != null) {
                                showErrorMsg(context, response.getErrors().get(0).getMessage());
                            } else {
                                if (response.getData().invoice_getInvoiceById() != null) {
                                    InvoiceItemResponse invoice = gson.fromJson(gson.toJson(response.getData().invoice_getInvoiceById()), InvoiceItemResponse.class);
                                    callback.onSuccess(invoice);
                                } else {
                                    callback.onFailed(context.getString(R.string.sorry_we_were_unable_to_process_your_payment));
                                }
                            }
                        }
                    });
                }

                @Override
                public void onFailure(@NotNull ApolloException e) {
                    callback.onFailed(e.getMessage());
                }
            });
        }
    }

    public void getExchangeRate(DataCallback<GetExchangeRateQuery.Sso_service> callback) {
        AppItem appItem = gson.fromJson(MySabaySDK.getInstance().getAppItem(), AppItem.class);
        Input<String> serviceCode = Input.fromNullable(MySabaySDK.getInstance().serviceCode());
        apolloClient.query(new GetExchangeRateQuery(serviceCode)).toBuilder()
                .requestHeaders(RequestHeaders.builder().addHeader("Authorization", "Bearer " + appItem.token).build())
                .build()
                .enqueue(new ApolloCall.Callback<GetExchangeRateQuery.Data>() {
                    @Override
                    public void onResponse(@NotNull Response<GetExchangeRateQuery.Data> response) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (response.getErrors() != null) {
                                    callback.onFailed(response.getErrors().get(0).getMessage());
                                } else {
                                    if (response.getData() != null) {
                                        callback.onSuccess(response.getData().sso_service().get(0));
                                    } else {
                                        callback.onFailed("Get Exchange rate failed");
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NotNull ApolloException e) {
                        callback.onFailed(e.getMessage());
                    }
                });
    }

    /**
     * show list all bank provider
     *
     * @param context
     */
    public void get3PartyCheckout(@NotNull Context context) {
        if (getMySabayProvider().getValue() == null) return;

        List<MySabayItemResponse> mySabayItem = getMySabayProvider().getValue();
        List<ProviderResponse> result = new ArrayList<>();

        for (MySabayItemResponse item : mySabayItem) {
            if (item.type.equals(Globals.ONE_TIME_PROVIDER)) {
                for (ProviderResponse providerResponse: item.providers) {
                    result.add(providerResponse);
                }
            }
        }
        _thirdPartyItemMediatorLiveData.setValue(result);
    }

    public ProviderResponse getInAppPurchaseProvider(String type) {
        if (getMySabayProvider().getValue() == null) return new ProviderResponse();

        ProviderResponse provider  = null;
        for (MySabayItemResponse item : getMySabayProvider().getValue()) {
            if (item.type.equals(Globals.IAP_PROVIDER)) {
                for (ProviderResponse providerItem: item.providers) {
                    if (type.equals(providerItem.code)) {
                        provider = providerItem;
                    }
                }
            }
        }
        return provider;
    }

    public ProviderResponse getMysabayProviderId(String type) {
        if (getMySabayProvider().getValue() == null) return new ProviderResponse();

        ProviderResponse provider  = null;
        for (MySabayItemResponse item : getMySabayProvider().getValue()) {
            if (item.type.equals(Globals.MY_SABAY_PROVIDER)) {
                for (ProviderResponse providerItem: item.providers) {
                    if (type.equals(providerItem.code)) {
                        provider = providerItem;
                    }
                }
            }
        }
        return provider;
    }

    public void showErrorMsg(ApolloException e, Context context, String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (e != null) {
                    if (e instanceof ApolloNetworkException) {
                        _networkState.setValue(new NetworkState(NetworkState.Status.ERROR, context.getString(R.string.msg_can_not_connect_server)));
                    } else {
                        _networkState.setValue(new NetworkState(NetworkState.Status.ERROR));
                        MessageUtil.displayDialog(context, message);
                    }
                } else {
                    _networkState.setValue(new NetworkState(NetworkState.Status.ERROR));
                    MessageUtil.displayDialog(context, message);
                }
            }
        });
    }

    public void showErrorMsg(Context context, String message) {
        showErrorMsg(null, context, message);
    }

}

