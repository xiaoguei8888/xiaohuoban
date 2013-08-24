package com.heibuddy.xiaohuoband;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.heibuddy.xiaohuoban.error.XiaohuobanException;
import com.heibuddy.xiaohuoban.util.LocationService;
import com.heibuddy.xiaohuoban.util.NewsService;
import com.heibuddy.xiaohuoban.util.NotificationsUtil;
import com.heibuddy.xiaohuoband.location.BestLocationListener;
import com.heibuddy.xiaohuoband.talk.BaseSendEntity;
import com.heibuddy.xiaohuoband.talk.ListItemAdapter;
import com.heibuddy.xiaohuoband.talk.BaseListItemEntity;
import com.heibuddy.xiaohuoband.talk.LocationMsgSendEntity;
import com.heibuddy.xiaohuoband.talk.QuestionItemEntity;
import com.heibuddy.xiaohuoband.talk.SimpleAnswerItemEntity;
import com.heibuddy.xiaohuoband.talk.TextMsgSendEntity;
import com.heibuddy.xiaohuoband.talk.NewsMsgSendEntity;
import com.heibuddy.xiaohuoband.talk.ThinkingItemEntity;
import com.heibuddy.xiaohuoband.talk.support.TalkProxy;

import com.heibuddy.xiaohuoband.R;

public class TalkActivity extends Activity {
    public static final String TAG = "TalkActivity";
    public static final boolean DEBUG = XiaohuobandSettings.DEBUG;

    public static final long MIN_GAP_TO_DISPLAY_NEWS = 3 * 60 * 60 * 1000;	//unit is millisecond
    public static final int MAX_DISPLAY_NEWS_TIME = 3;
    public static final float MIN_DISTANCE_TO_HOME = 1000.00f;
    public static final float MIN_DISTANCE_TO_LAST_LOCATION = 1000.00f;
    
    public static enum TalkQueryType {PUBTEXT, PUBLOCATION, PUBNEWS, UNKNOWN};
    private StateHolder mStateHolder = new StateHolder();
    private SearchLocationObserver mSearchLocationObserver = new SearchLocationObserver();
    
	private ListView mTalkView;
	private List<BaseListItemEntity> mList = null;
	private ListItemAdapter mListItemAdapter;
	private ImageView mAskButton;
	private EditText mEditText;
	
    private BroadcastReceiver mLoggedOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (DEBUG) Log.d(TAG, "onCreate()");
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Xiaohuoband.INTENT_ACTION_LOGGED_OUT));

		setContentView(R.layout.activity_first_tab);
		listInit();
		mEditText = (EditText) findViewById(R.id.fst_tab_edittext);
		mAskButton = (ImageView) findViewById(R.id.fst_tab_buttom);
		mAskButton.setOnClickListener(mButtonAskListener);
		
        mStateHolder = new StateHolder();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume");
        ((Xiaohuoband) getApplication()).requestLocationUpdates(mSearchLocationObserver);
        tryToCheckAndPullNews();
    }

    @Override
    public void onPause() {
        super.onPause();
        ((Xiaohuoband) getApplication()).removeLocationUpdates(mSearchLocationObserver);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }
    
    private void tryToCheckAndPullNews()
    {
		if (NewsService.isNeedingToPullNews(this, MIN_GAP_TO_DISPLAY_NEWS, MAX_DISPLAY_NEWS_TIME) || DEBUG)
		{
			if (DEBUG) Log.d(TAG, "Need to pull news");
            startTask(TalkQueryType.PUBNEWS);
		}
		else
		{
			if (DEBUG) Log.d(TAG, "No need to pull news");
		}
    }
    
    private void startTask(TalkQueryType type) {
    	switch (type)
        {
            case PUBTEXT:
            	String query = mEditText.getText().toString();
            	mEditText.setText("");
            	mStateHolder.startTask(this, TalkQueryType.PUBTEXT, query);
            	break;
            case PUBLOCATION:
            	mStateHolder.startTask(this, TalkQueryType.PUBLOCATION, "");
            	break;
            case PUBNEWS:
            	mStateHolder.startTask(this, TalkQueryType.PUBNEWS, "");
            	break;
            default:
            	break;
       }
    }
    
	private void listInit()
	{
		mTalkView = (ListView) findViewById(R.id.list_first_tab);
		mList = new ArrayList<BaseListItemEntity>();
		mListItemAdapter = new ListItemAdapter(TalkActivity.this, mList);
		
		//这之后可以删
		mList.add(new QuestionItemEntity("这是提问"));
		mList.add(new SimpleAnswerItemEntity("这是应答的文本数据，不带链接"));
		mList.add(new QuestionItemEntity("问"));
		mList.add(new SimpleAnswerItemEntity("带链接的文本1111111111111111","http://m.baidu.com"));
		mList.add(new QuestionItemEntity("问"));
		mList.add(new SimpleAnswerItemEntity("这是应答的文本数据，不带链接"));
		mTalkView.setAdapter(mListItemAdapter);
		//这之前可以删除
		
		mTalkView.setDivider(null);
	}

	private OnClickListener mButtonAskListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (!mEditText.getText().toString().equals("")) {
				startTask(TalkQueryType.PUBTEXT);
			}
		}
	};

	private void addRecvMsg(BaseListItemEntity listItemEntity){
		mList.add(listItemEntity);
		mTalkView.setAdapter(mListItemAdapter);
	}

    /** If location changes, auto-start a nearby venues search. */
    private class SearchLocationObserver implements Observer {
        private boolean mRequestedFirstSearch = false;

        @Override
        public void update(Observable observable, Object data) {
            Location location = (Location) data;
            // Fire a search if we haven't done so yet.
            if (!mRequestedFirstSearch
                    && ((BestLocationListener) observable).isAccurateEnough(location)) {
                mRequestedFirstSearch = true;
                
                boolean isNeed = LocationService.isGoingFarAway((Xiaohuoband) getApplication(), 
                												 location, 
                												 MIN_DISTANCE_TO_HOME, 
                												 MIN_DISTANCE_TO_LAST_LOCATION); 
                if (isNeed || DEBUG) {
                	startTask(TalkQueryType.PUBLOCATION);
                }
            }
        }
    }
    
	private Handler updateHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			if (msg.obj instanceof BaseListItemEntity)
			{
				BaseListItemEntity listItemEntity = (BaseListItemEntity)msg.obj;
				if (!mList.isEmpty()){
					BaseListItemEntity i= mList.get(mList.size() - 1);
					if (i instanceof ThinkingItemEntity)
					{
						mList.remove(mList.size() - 1);
					}
				}
				addRecvMsg(listItemEntity);
			}
			else if (msg.obj instanceof ButtonProxy) 
			{
				ButtonProxy bp = (ButtonProxy)msg.obj;
				mAskButton.setEnabled(bp.mIsEnable);
			}
		}
	};
	
	private static class ButtonProxy {
		public boolean mIsEnable;
		
		public ButtonProxy(boolean isEnable){
			mIsEnable = isEnable;
		}
	}
	
    private static class GetMsgTask implements Runnable {
        private static final String TAG = "GetMsgTask";
        private static final boolean DEBUG = XiaohuobandSettings.DEBUG;

        private TalkActivity mActivity;
        private Exception mReason;
        private TalkQueryType mQueryType;
        private String mQuery;

        public GetMsgTask(TalkActivity activity, TalkQueryType type, String query) {
            super();
            mActivity = activity;
            mQueryType = type;
            mQuery = query;
        }
        
        protected void onPreExecute() {
            if (DEBUG) Log.d(TAG, "onPreExecute()");
            Log.d(TAG, "This guy is running: " + mQueryType.toString());
            
            Message msg;
            switch (mQueryType)
            {
            	case PUBTEXT:
            		msg = mActivity.updateHandler.obtainMessage(0, new QuestionItemEntity(mQuery));
            		mActivity.updateHandler.sendMessage(msg);
            		msg = mActivity.updateHandler.obtainMessage(0, new ThinkingItemEntity("让我想想..."));
            		mActivity.updateHandler.sendMessage(msg);
            		break;
            	case PUBLOCATION:
            		msg = mActivity.updateHandler.obtainMessage(0, new QuestionItemEntity("正在获取周边信息..."));
            		mActivity.updateHandler.sendMessage(msg);
            		msg = mActivity.updateHandler.obtainMessage(0, new ThinkingItemEntity("稍等哈..."));
            		mActivity.updateHandler.sendMessage(msg);
            		break;
            	case PUBNEWS:
            		msg =mActivity.updateHandler.obtainMessage(0, new QuestionItemEntity("正在获取新闻..."));
            		mActivity.updateHandler.sendMessage(msg);
            		msg = mActivity.updateHandler.obtainMessage(0, new ThinkingItemEntity("进行ing..."));
            		mActivity.updateHandler.sendMessage(msg);
            		break;
            	default:
            		break;
            }
        }

        private LocationMsgSendEntity getLocationMsgInstance(String fromUserName, long now, String userId){
			// Get last known location.
            Location location = ((Xiaohuoband) mActivity.getApplication()).getLastKnownLocation();
            if (location == null) {
            	mReason = new XiaohuobanException("No location!");
            	return null;
            }
            
            double locX = location.getLatitude();
            double locY = location.getLongitude();
            double scale = 0.0f;
            String label = "";
            return new LocationMsgSendEntity("server", fromUserName, String.valueOf(now), userId, 
            													mQuery, locX, locY, scale, label);
        }
        
        protected BaseListItemEntity doInBackground() {
            if (DEBUG) Log.d(TAG, "doInBackground()");
            
            Xiaohuoband xiaohuoband = (Xiaohuoband) mActivity.getApplication();
			String fromUserName = xiaohuoband.getUserName();
			long now = new Date().getTime() / 1000;	//in seconds
			String userId = xiaohuoband.getUserId();    
			
			BaseSendEntity msg = null;
            switch (mQueryType)
            {
            	case PUBTEXT:
            		msg = new TextMsgSendEntity("server", fromUserName, String.valueOf(now), userId, 
							   mQuery, false);
            		break;
            	case PUBLOCATION:
            		msg = getLocationMsgInstance(fromUserName, now, userId);
            		break;
            	case PUBNEWS:
            		msg = new NewsMsgSendEntity("server", fromUserName, String.valueOf(now), userId);
            		break;
            	default:
            		break;
            }
            
            BaseListItemEntity entity = null;
            try {
            	entity = TalkProxy.getAnswer(msg, mActivity);
            }catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Caught Exception logging in.", e);
                mReason = e;
            }
            
            return entity;
        }

        protected void onPostExecute(BaseListItemEntity entity) {
            if (entity != null)
            {
                if (DEBUG) Log.d(TAG, "onPostExecute(): " + entity);
                mActivity.updateHandler.sendMessage(mActivity.updateHandler.obtainMessage(0, entity));
                if (mQueryType == TalkQueryType.PUBNEWS)
                {
                	if (DEBUG) return;
                	if (!NewsService.updateLastDisplayTimeToPreferencesDB(mActivity, new Date().getTime()))
                	{
                		Log.e(TAG, "updateLastDisplayTimeToPreferencesDB failed!");
                	}
                	int displayedCountToday = NewsService.getTodayNewsDisplayTimesFromPreferencesDB(mActivity);
                	if (!NewsService.updateTodayNewsDisplayTimesToPreferencesDB(mActivity, displayedCountToday+1))
                	{
                		Log.e(TAG, "updateTodayNewsDisplayTimesToPreferencesDB failed!");
                	}
                }
            }
            else
            {
            	Message msg = mActivity.updateHandler.obtainMessage(0, new SimpleAnswerItemEntity("目测网络有问题啊..."));
            	mActivity.updateHandler.sendMessage(msg);
            	if (DEBUG) Log.d(TAG, "Oops, listItemEntity is null");
            	NotificationsUtil.ToastReasonForFailure(mActivity, mReason);
            }
        }

		@Override
		public void run() {
          	Log.d(TAG, "This guy is running: " + mQueryType.toString());
          	mActivity.updateHandler.sendMessage(mActivity.updateHandler.obtainMessage(0, new ButtonProxy(false)));
          	
			try
			{
				onPreExecute();
				BaseListItemEntity entity = doInBackground();
				onPostExecute(entity);
			}finally
			{
				mActivity.updateHandler.sendMessage(mActivity.updateHandler.obtainMessage(0, new ButtonProxy(true)));
				Log.d(TAG, "This guy ends: " + mQueryType.toString());
			}
		}
    }

    private static class StateHolder {
        private GetMsgTask mGetMsgTask;
        private ExecutorService mExecutorService;
      
        public StateHolder() {
        	mGetMsgTask = null;
        	mExecutorService = Executors.newSingleThreadExecutor();
        }
      
        public void startTask(TalkActivity activity, TalkQueryType type, String query) {
        	mGetMsgTask = new GetMsgTask(activity, type, query);
        	mExecutorService.execute(mGetMsgTask);
        }
    }    
}
