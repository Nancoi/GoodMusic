package com.llw.goodmusic.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;
import com.llw.goodmusic.MusicApplication;
import com.llw.goodmusic.R;
import com.llw.goodmusic.adapter.SongNameAdapter;
import com.llw.goodmusic.basic.BasicActivity;
import com.llw.goodmusic.bean.ChangeUI;
import com.llw.goodmusic.bean.Song;
import com.llw.goodmusic.databinding.ActivityMainBinding;
import com.llw.goodmusic.livedata.LiveDataBus;
import com.llw.goodmusic.service.MusicService;
import com.llw.goodmusic.utils.BLog;
import com.llw.goodmusic.utils.Constant;
import com.llw.goodmusic.utils.MusicUtils;
import com.llw.goodmusic.utils.SPUtils;
import com.llw.goodmusic.view.MusicRoundProgressView;

import org.litepal.LitePal;

import java.lang.reflect.Field;
import java.util.List;

import static com.llw.goodmusic.utils.Constant.CLOSE;
import static com.llw.goodmusic.utils.Constant.NEXT;
import static com.llw.goodmusic.utils.Constant.PAUSE;
import static com.llw.goodmusic.utils.Constant.PLAY;
import static com.llw.goodmusic.utils.Constant.PREV;
import static com.llw.goodmusic.utils.Constant.PROGRESS;

/**
 * 主页面
 *
 * @author llw
 */
public class MainActivity extends BasicActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    /**
     * 时间
     */
    private long timeMillis;

    private MusicService.MusicBinder musicBinder;

    private MusicService musicService;

    private LinearLayout layLocalMusic;

    /**
     * 底部logo图标，点击之后弹出当前播放歌曲详情页
     */
    private ShapeableImageView ivLogo;

    /**
     * 底部当前播放歌名
     */
    private MaterialTextView tvDefaultName;

    /**
     * 底部VP左右滑动切换歌曲，VP的item里面是歌名+歌手
     */
    private ViewPager2 vpChangeSong;

    /**
     * 底部当前歌曲控制按钮, 播放和暂停
     */
    private MaterialButton btnPlay;
    /**
     * 自定义进度条
     */
    private MusicRoundProgressView musicProgress;

    /**
     * 本地音乐数量
     */
    private TextView tvLocalMusicNum;


    private List<Song> mList;


    /**
     * 列表位置
     */
    private int listPosition = 0;

    /**
     * 当Service中通知栏有变化时接收到消息
     */
    private LiveDataBus.BusMutableLiveData<String> activityLiveData;

    /**
     * 当在Activity中做出播放状态的改变时，通知做出相应改变
     */
    private LiveDataBus.BusMutableLiveData<String> notificationLiveData;

    /**
     * 歌名适配器
     */
    private SongNameAdapter songNameAdapter;

    /**
     * VP2的滑动监听
     */
    private PageChangeCallback pageChangeCallback;

    @Override
    public void initData(Bundle savedInstanceState) {
        BLog.d(TAG, "initData");
        ActivityMainBinding binding = DataBindingUtil.setContentView(context, R.layout.activity_main);
        layLocalMusic = binding.layLocalMusic;
        tvLocalMusicNum = binding.tvLocalMusicNum;
        ivLogo = binding.playControlLayout.ivLogo;
        tvDefaultName = binding.playControlLayout.tvDefaultName;
        vpChangeSong = binding.playControlLayout.vp2ChangeSong;
        btnPlay = binding.playControlLayout.btnPlay;
        musicProgress = binding.playControlLayout.musicProgress;
        layLocalMusic.setOnClickListener(this);
        btnPlay.setOnClickListener(this);

        //绑定服务
        Intent serviceIntent = new Intent(context, MusicService.class);
        bindService(serviceIntent, connection, BIND_AUTO_CREATE);

        //通知栏的观察者
        notificationObserver();
        //控制通知栏
        notificationLiveData = LiveDataBus.getInstance().with("notification_control", String.class);

    }

    /**
     * 初始化ViewPager2
     */
    private void initViewPager2() {
        //实例化VP变化回调
        pageChangeCallback = new PageChangeCallback();
        songNameAdapter = new SongNameAdapter(R.layout.item_song_name, mList);
        vpChangeSong.setAdapter(songNameAdapter);
        vpChangeSong.registerOnPageChangeCallback(pageChangeCallback);
        songNameAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BLog.d(TAG, "onStart");
        if (musicService != null) {
            changeUI(musicService.getPlayPosition());
            if (musicService.mediaPlayer != null) {
                if (musicService.mediaPlayer.isPlaying()) {
                    btnPlay.setIcon(getDrawable(R.mipmap.icon_pause));
                    btnPlay.setIconTint(getColorStateList(R.color.gold_color));
                } else {
                    btnPlay.setIcon(getDrawable(R.mipmap.icon_play));
                    btnPlay.setIconTint(getColorStateList(R.color.white));
                }
            }
        }

    }

    /**
     * 通知栏动作观察者
     */
    private void notificationObserver() {
        activityLiveData = LiveDataBus.getInstance().with("activity_control", String.class);
        activityLiveData.observe(MainActivity.this, true, new Observer<String>() {
            @Override
            public void onChanged(String state) {

                switch (state) {
                    case PLAY:
                        btnPlay.setIcon(getDrawable(R.mipmap.icon_pause));
                        btnPlay.setIconTint(getColorStateList(R.color.gold_color));
                        BLog.d(TAG,state);
                        changeUI(musicService.getPlayPosition());
                        break;
                    case PAUSE:
                    case CLOSE:
                        btnPlay.setIcon(getDrawable(R.mipmap.icon_play));
                        btnPlay.setIconTint(getColorStateList(R.color.white));
                        changeUI(musicService.getPlayPosition());
                        break;
                    case PREV:
                        BLog.d(TAG, "上一曲");
                        changeUI(musicService.getPlayPosition());
                        break;
                    case NEXT:
                        BLog.d(TAG, "下一曲");
                        changeUI(musicService.getPlayPosition());
                        break;
                    case PROGRESS:
                        //播放进度发生改变时,只改变进度，不改变其他
                        musicProgress.setProgress(musicService.mediaPlayer.getCurrentPosition(), musicService.mediaPlayer.getDuration());
                        break;
                    default:
                        break;
                }

            }
        });
    }

    /**
     * 更改页面的UI状态
     */
    private void changeUI(int position) {
        if (mList != null && mList.size() > 0) {
            listPosition = position;

            Song song = mList.get(position);

            if(SPUtils.getBoolean(Constant.IS_CHANGE,true,context)){
                //切换选中的item
                vpChangeSong.setCurrentItem(position, false);
            }

            //设置歌曲所在专辑的封面图片
            ivLogo.setImageBitmap(MusicUtils.getAlbumPicture(context, song.getPath(), 1));
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        BLog.d(TAG, "onResume");
        mList = LitePal.findAll(Song.class);
        tvLocalMusicNum.setText(String.valueOf(mList.size()));
        //初始化ViewPager2，赋值，剩下的交给卧底去处理播放信息
        initViewPager2();
    }


    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.lay_local_music:
                //本地音乐
                startActivity(new Intent(context, LocalMusicActivity.class));
                break;
            case R.id.btn_play:
                if (mList.size() == 0) {
                    show("没有可播放的音乐,请到 “本地音乐” 进行扫描");
                    return;
                }
                tvDefaultName.setVisibility(View.GONE);

                //点击主页面时的播放按钮时先判断当前是否正在播放歌曲
                if (musicService.mediaPlayer == null) {
                    //则说明没有播放过歌曲,所以调用play方法进行播放
                    musicService.play(listPosition);
                } else {
                    //如果已经播放过歌曲，肯定会有当前播放歌曲的位置，如果是播放状态，则点击暂停，反之则播放
                    //通过观察者模式，
                    notificationLiveData.postValue(PLAY);
                }

                break;
            default:
                break;
        }
    }


    /**
     * 服务连接
     */
    private ServiceConnection connection = new ServiceConnection() {

        /**
         * 连接服务
         * @param name
         * @param service
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicBinder = (MusicService.MusicBinder) service;
            musicService = musicBinder.getService();
            BLog.d(TAG, "Service与Activity已连接");

        }

        //断开服务
        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBinder = null;
        }
    };


    /**
     * ViewPager2 的滑动监听回调
     */
    class PageChangeCallback extends OnPageChangeCallback {

        private int pos = 0;
        private boolean isScrolled;

        /**
         * 滑动状态更改
         *
         * @param state
         */
        @Override
        public void onPageScrollStateChanged(int state) {
            BLog.d(TAG,state+"");

            if (state == ViewPager2.SCROLL_STATE_SETTLING) {
                isScrolled = false;
            }else if(state == ViewPager2.SCROLL_STATE_DRAGGING){
                //人为滑动
                SPUtils.putBoolean(Constant.IS_CHANGE,false,context);
            } else if (state == ViewPager2.SCROLL_STATE_IDLE && isScrolled) {
                //此处为你需要的情况，再加入当前页码判断可知道是第一页还是最后一页
                if (mList.size() != 1 && pos == (mList.size() - 1)) {

                    BLog.d(TAG, "切换到第一首");

                    vpChangeSong.setCurrentItem(0, false);

                } else if (mList.size() != 1 && pos == 0) {

                    BLog.d(TAG, "切换到最后一首");
                    vpChangeSong.setCurrentItem(mList.size() - 1, false);

                }
            } else {
                isScrolled = true;

            }
        }

        /**
         * 页面选中
         *
         * @param position
         */
        @Override
        public void onPageSelected(int position) {

            pos = position;

            if(musicService == null){
                return;
            }
            musicService.play(position);

        }
    }

    /**
     * 增加一个退出应用的提示
     *
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis() - timeMillis) > 2000) {
                show("再按一次退出 GoodMusic");
                timeMillis = System.currentTimeMillis();
            } else {
                MusicApplication.getActivityManager().finishAll();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        System.exit(0);
    }

}
