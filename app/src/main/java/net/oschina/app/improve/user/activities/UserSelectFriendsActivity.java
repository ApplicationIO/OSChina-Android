package net.oschina.app.improve.user.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.improve.app.ParentLinkedHolder;
import net.oschina.app.improve.base.activities.BaseBackActivity;
import net.oschina.app.improve.bean.simple.Author;
import net.oschina.app.improve.tweet.fragments.TweetPublishFragment;
import net.oschina.app.improve.user.adapter.UserSearchFriendsAdapter;
import net.oschina.app.improve.user.adapter.UserSelectFriendsAdapter;
import net.oschina.app.improve.user.bean.UserFriend;
import net.oschina.app.improve.user.helper.ContactsCacheManager;
import net.oschina.app.improve.user.helper.SyncFriendHelper;
import net.oschina.app.improve.utils.AssimilateUtils;
import net.oschina.app.improve.widget.RecentContactsView;
import net.oschina.app.ui.empty.EmptyLayout;
import net.oschina.app.util.TDevice;
import net.oschina.app.widget.IndexView;
import net.oschina.common.utils.CollectionUtil;
import net.oschina.common.widget.RichEditText;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import butterknife.Bind;

/**
 * 用户联系人列表
 */
public class UserSelectFriendsActivity extends BaseBackActivity
        implements RecentContactsView.OnSelectedChangeListener, ContactsCacheManager.OnSelectedChangeListener,
        IndexView.OnIndexTouchListener, SearchView.OnQueryTextListener, UserSearchFriendsAdapter.onKeyboardListener {

    @Bind(R.id.searcher_friends)
    SearchView mSearchView;

    @Bind(R.id.search_mag_icon)
    ImageView mSearchIcon;

    @Bind(R.id.search_edit_frame)
    LinearLayout mLayoutEditFrame;

    @Bind(R.id.recycler_friends_icon)
    HorizontalScrollView mHorizontalScrollView;

    @Bind(R.id.select_container)
    LinearLayout mSelectContainer;

    @Bind(R.id.tv_label)
    TextView mTvLabel;

    @Bind(R.id.tv_no_friends)
    TextView mTvNoFriends;

    @Bind(R.id.recycler_friends)
    RecyclerView mRecyclerFriends;
    @Bind(R.id.tv_index_show)
    TextView mTvIndexShow;

    @Bind(R.id.lay_index)
    IndexView mIndex;

    @Bind(R.id.lay_error)
    EmptyLayout mEmptyLayout;

    //选中icon缓存朋友数据
    private LinkedList<Author> mSelectFriendList = new LinkedList<>();

    // 最近联系人
    private RecentContactsView mRecentView;

    //网络初始化的adapter
    private UserSelectFriendsAdapter mLocalAdapter = null;
    private UserSearchFriendsAdapter mSearchAdapter;

    private static ParentLinkedHolder<RichEditText> textParentLinkedHolder;

    public static void show(Object starter, RichEditText editText) {
        if (editText != null && (starter instanceof Activity || starter instanceof Fragment || starter instanceof android.app.Fragment)) {
            synchronized (UserSelectFriendsActivity.class) {
                ParentLinkedHolder<RichEditText> holder = new ParentLinkedHolder<>(editText);
                textParentLinkedHolder = holder.addParent(textParentLinkedHolder);
            }

            if (starter instanceof Activity) {
                Activity context = (Activity) starter;
                Intent intent = new Intent(context, UserSelectFriendsActivity.class);
                context.startActivityForResult(intent, TweetPublishFragment.REQUEST_CODE_SELECT_FRIENDS);
            } else if (starter instanceof Fragment) {
                Fragment fragment = (Fragment) starter;
                Context context = fragment.getContext();
                if (context == null)
                    return;
                Intent intent = new Intent(context, UserSelectFriendsActivity.class);
                fragment.startActivityForResult(intent, TweetPublishFragment.REQUEST_CODE_SELECT_FRIENDS);
            } else {
                android.app.Fragment fragment = (android.app.Fragment) starter;
                Context context = fragment.getActivity();
                if (context == null)
                    return;
                Intent intent = new Intent(context, UserSelectFriendsActivity.class);
                fragment.startActivityForResult(intent, TweetPublishFragment.REQUEST_CODE_SELECT_FRIENDS);
            }
        }
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_main_user_select_friends;
    }

    @Override
    protected void initWidget() {
        super.initWidget();

        // 初始化最近联系人
        mRecentView = new RecentContactsView(this);
        mRecentView.setListener(this);

        //初始化searchView的搜索icon
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mSearchIcon.getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mSearchIcon.setLayoutParams(params);

        LinearLayout.LayoutParams params1 = (LinearLayout.LayoutParams) mLayoutEditFrame.getLayoutParams();
        params1.setMargins(0, 0, 0, 0);
        mLayoutEditFrame.setLayoutParams(params1);
        mSearchView.setOnQueryTextListener(this);

        mEmptyLayout.setLoadingFriend(true);
        mEmptyLayout.setOnLayoutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EmptyLayout emptyLayout = mEmptyLayout;
                if (emptyLayout != null && emptyLayout.getErrorState() != EmptyLayout.HIDE_LAYOUT) {
                    emptyLayout.setErrorType(EmptyLayout.NETWORK_LOADING);
                    initDataFromCacheOrNet();
                }
            }
        });

        mRecyclerFriends.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerFriends.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                TDevice.hideSoftKeyboard(mSearchView);
                return false;
            }
        });

        mLocalAdapter = new UserSelectFriendsAdapter(this, mRecentView, this);

        mSearchAdapter = new UserSearchFriendsAdapter(UserSelectFriendsActivity.this);
        mSearchAdapter.setOnKeyboardListener(this);
        //mSearchAdapter.setOnFriendSelector(this);

        mRecyclerFriends.setAdapter(mLocalAdapter);

        mIndex.setOnIndexTouchListener(this);

        mTvLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSelectData(true);
            }
        });

        // 初始化trigger
        recentSelectedTrigger = mRecentView;
        adapterSelectedTrigger = mLocalAdapter;
    }

    @Override
    protected void initData() {
        super.initData();
        mEmptyLayout.post(new Runnable() {
            @Override
            public void run() {
                initDataFromCacheOrNet();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tweet_topic, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_submit) {
            sendSelectData(false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onIndexTouchUp() {
        mTvIndexShow.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (UserSelectFriendsActivity.class) {
            if (textParentLinkedHolder != null) {
                textParentLinkedHolder = textParentLinkedHolder.putParent();
            }
        }
    }

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @Override
    public void onIndexTouchMove(char indexLetter) {
        String str = Character.toString(indexLetter);
        List<UserFriend> userFriends = mLocalAdapter.getItems();
        int position = 0;
        for (int i = userFriends.size() - 1; i > 0; i--) {
            UserFriend friend = userFriends.get(i);
            if (friend.getShowLabel().startsWith(str)) {
                position = i;
                break;
            }
        }

        mRecyclerFriends.smoothScrollToPosition(position);

        mTvIndexShow.setText(str);
        mTvIndexShow.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("RestrictedApi")
    @Override
    protected void onStop() {
        super.onStop();
        mSearchView.clearFocus();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onQueryTextChange(String newText) {

        if (TextUtils.isEmpty(newText)) {
            mTvLabel.setText(null);
            mTvLabel.setVisibility(View.GONE);
            if (mLocalAdapter.getItemCount() == 0) {
                mIndex.setVisibility(View.GONE);
                mTvNoFriends.setVisibility(View.VISIBLE);
            } else {
                mIndex.setVisibility(View.VISIBLE);
                mTvNoFriends.setVisibility(View.GONE);
            }

            //当直接在搜索界面删除信息时，
            //for (UserFriend cacheIconFriend : mSelectFriendList) {
            //    mLocalAdapter.updateSelectStatus(cacheIconFriend, true);
            //}

            if (mSelectFriendList.size() == 0) {
                mLocalAdapter.updateAllSelectStatus(false);
            } else {
                //mLocalAdapter.updateSelectCount(mSelectFriendList);
            }

            mRecyclerFriends.setAdapter(mLocalAdapter);

            mSearchAdapter.clear();
            mSearchAdapter.notifyDataSetChanged();
            mSearchAdapter.setSearchContent(newText);

            TDevice.hideSoftKeyboard(mSearchView);

            return true;
        } else {

            TDevice.showSoftKeyboard(mSearchView);

            mTvNoFriends.setVisibility(View.GONE);

            if (mIndex.getVisibility() == View.VISIBLE) {
                mIndex.setVisibility(View.GONE);
            }
            mTvLabel.setText("@" + newText);
            mTvLabel.setVisibility(View.VISIBLE);

            mSearchAdapter.clear();
            mSearchAdapter.setSearchContent(newText);

            if (mRecyclerFriends.getAdapter() instanceof UserSelectFriendsAdapter) {
                mRecyclerFriends.setAdapter(mSearchAdapter);
            }
            //mSearchAdapter.setOnFriendSelector(this);

            queryUpdateView(newText);
        }
        return true;
    }


    @Override
    public void hideKeyboard() {
        TDevice.hideSoftKeyboard(mSearchView);
    }

    /**
     * request data
     */
    private void initDataFromCacheOrNet() {
        final ArrayList<UserFriend> friends = SyncFriendHelper.getFriends();
        if (friends != null && friends.size() > 0) {
            displayFirstView(friends);
        } else {
            //检查网络
            if (!checkNetIsAvailable()) {
                showError(EmptyLayout.NETWORK_ERROR);
            } else {
                SyncFriendHelper.load(new Runnable() {
                    @Override
                    public void run() {
                        mEmptyLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                ArrayList<UserFriend> friends = SyncFriendHelper.getFriends();
                                displayFirstView(friends);
                            }
                        });

                    }
                });
            }
        }
    }

    /**
     * refresh the friends ui
     *
     * @param friends friends
     */
    private void displayFirstView(ArrayList<UserFriend> friends) {
        // 没有拉取到用户，但是有最近联系人也显示界面
        if ((friends != null && friends.size() > 0) || mRecentView.hasData()) {
            mLocalAdapter.initItems(friends);
            mTvNoFriends.setVisibility(View.GONE);
        } else {
            showError(EmptyLayout.NODATA);
            mIndex.setVisibility(View.GONE);
            mTvNoFriends.setVisibility(View.VISIBLE);
        }

        hideLoading();
    }

    /**
     * refresh the query ui
     *
     * @param queryText query text
     */
    private void queryUpdateView(String queryText) {

        //初始化缓存本地搜索好友列表
        ArrayList<UserFriend> searchFriends = new ArrayList<>();

        UserFriend LocalUserFriend = new UserFriend();
        LocalUserFriend.setName(getString(R.string.local_search_label));
        LocalUserFriend.setShowLabel(getString(R.string.local_search_label));
        LocalUserFriend.setShowViewType(UserSelectFriendsAdapter.INDEX_TYPE);
        searchFriends.add(LocalUserFriend);

        UserFriend NetUserFriend = new UserFriend();
        NetUserFriend.setName(getString(R.string.net_search_label));
        NetUserFriend.setShowLabel(getString(R.string.search_net_label));
        NetUserFriend.setShowViewType(UserSelectFriendsAdapter.SEARCH_TYPE);
        searchFriends.add(NetUserFriend);

        if (!TextUtils.isEmpty(queryText)) {
            String pinyinQueryText = AssimilateUtils.returnPinyin(queryText, false);
            //缓存的本地好友列表
            List<UserFriend> cacheFriends = mLocalAdapter.getItems();

            if (cacheFriends != null) {
                for (UserFriend friend : cacheFriends) {
                    String name = friend.getName();
                    if (TextUtils.isEmpty(name)) continue;

                    String pingYin = AssimilateUtils.returnPinyin4(name, true);
                    boolean isZH = AssimilateUtils.checkIsZH(queryText);

                    boolean isMatch;
                    if (isZH) {
                        isMatch = name.contains(queryText);
                    } else {
                        isMatch = pingYin.startsWith(pinyinQueryText) || pingYin.contains(" " + pinyinQueryText);
                    }

                    //搜索列表当中没有该条数据，进行添加
                    if (isMatch) {
                        friend.setShowLabel(name);
                        friend.setShowViewType(UserSelectFriendsAdapter.USER_TYPE);
                        searchFriends.add(1, friend);
                    }
                }
            }
        }

        mSearchAdapter.clear();
        mSearchAdapter.addItems(searchFriends);
        //mSearchAdapter.setSelectIcons(mSelectFriendList);
    }

    /**
     * send select data to  publish tweet
     */
    private void sendSelectData(boolean isLabel) {
        String queryLabel = (String) mTvLabel.getText();
        List<String> friendNames = new ArrayList<>();
        if (isLabel) {
            if (!TextUtils.isEmpty(queryLabel)) {
                queryLabel = queryLabel.substring(1);
                friendNames.add(queryLabel);
            }
        }

        for (Author author : mSelectFriendList) {
            friendNames.add(author.getName());
        }

        String[] names = CollectionUtil.toArray(friendNames, String.class);

        synchronized (UserSelectFriendsActivity.class) {
            if (textParentLinkedHolder != null) {
                RichEditText editText = textParentLinkedHolder.item;
                if (editText != null)
                    editText.appendMention(names);
            }
        }

        // 回调前进行最近联系人存储
        RecentContactsView.add(CollectionUtil.toArray(mSelectFriendList, Author.class));

        Intent result = new Intent();
        result.putExtra("data", names);
        setResult(RESULT_OK, result);

        finish();
    }

    private boolean checkNetIsAvailable() {
        if (!TDevice.hasInternet()) {
            AppContext.showToastShort(getString(R.string.tip_network_error));
            showError(EmptyLayout.NETWORK_ERROR);
            return false;
        }
        return true;
    }

    private void hideLoading() {
        final EmptyLayout emptyLayout = mEmptyLayout;
        if (emptyLayout != null)
            emptyLayout.setErrorType(EmptyLayout.HIDE_LAYOUT);
    }

    private void showError(int type) {
        EmptyLayout layout = mEmptyLayout;
        if (layout != null) {
            layout.setErrorType(type);
        }
    }


    /**
     * 刷新选中的布局
     */
    private void updateSelectView() {
        mSelectContainer.removeAllViews();

        List<Author> authors = this.mSelectFriendList;
        for (final Author author : authors) {
            ImageView ivIcon = (ImageView) LayoutInflater.from(this)
                    .inflate(R.layout.activity_main_select_friend_label_container_item, mSelectContainer, false);

            ivIcon.setTag(R.id.iv_show_icon, author);
            ivIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Author tag = (Author) v.getTag(R.id.iv_show_icon);
                    onSelectIconClick(tag);
                    mSelectContainer.removeView(v);
                }
            });
            mSelectContainer.addView(ivIcon);
            Glide.with(this).load(author.getPortrait()).error(R.mipmap.widget_default_face).into(ivIcon);
        }
    }

    private void onSelectIconClick(Author author) {
        // 通知适配器
        adapterSelectedTrigger.trigger(author, false);
        // 通知最近联系人
        recentSelectedTrigger.trigger(author, false);
    }

    private ContactsCacheManager.SelectedTrigger<RecentContactsView.Model> recentSelectedTrigger;
    private ContactsCacheManager.SelectedTrigger<ContactsCacheManager.Friend> adapterSelectedTrigger;

    /**
     * 尝试插入一个选中，如果不允许则不插入，并返回false
     */
    private boolean tryInsertSelected(Author author) {
        boolean allow = mSelectFriendList.size() < 10;
        if (allow) {
            mSelectFriendList.add(author);
            updateSelectView();
        } else
            AppContext.showToastShort(getString(R.string.check_count_hint));
        return allow;
    }

    /**
     * 移除选中
     */
    private void removeSelected(Author author) {
        mSelectFriendList.remove(author);
        updateSelectView();
    }

    /**
     * 最近联系人触发
     */
    @Override
    public void tryTriggerSelected(RecentContactsView.Model model, ContactsCacheManager.SelectedTrigger<RecentContactsView.Model> trigger) {
        if (ContactsCacheManager.checkNotInContacts(mSelectFriendList, model.author)) {
            if (tryInsertSelected(model.author)) {
                trigger.trigger(model, true);
                // 通知适配器
                adapterSelectedTrigger.trigger(model.author, true);
            }
        } else {
            removeSelected(model.author);
            trigger.trigger(model, false);
            // 通知适配器
            adapterSelectedTrigger.trigger(model.author, false);
        }
    }

    /**
     * 适配器触发
     */
    @Override
    public void tryTriggerSelected(ContactsCacheManager.Friend friend, ContactsCacheManager.SelectedTrigger<ContactsCacheManager.Friend> trigger) {
        if (ContactsCacheManager.checkNotInContacts(mSelectFriendList, friend.author)) {
            if (tryInsertSelected(friend.author)) {
                trigger.trigger(friend, true);
                // 通知最近联系人
                recentSelectedTrigger.trigger(friend.author, true);
            }
        } else {
            removeSelected(friend.author);
            trigger.trigger(friend, false);
            // 通知最近联系人
            recentSelectedTrigger.trigger(friend.author, false);
        }
    }
}
