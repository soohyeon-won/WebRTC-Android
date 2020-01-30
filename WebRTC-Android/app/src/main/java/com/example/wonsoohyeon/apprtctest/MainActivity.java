package com.example.wonsoohyeon.apprtctest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.net.wifi.aware.AttachCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.support.v7.app.*;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.HardwareVideoDecoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.RendererCommon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.webrtc.MediaCodecVideoDecoder;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SignallingClient.SignalingInterface, View.OnTouchListener{
    Context mContext = this;
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;

    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;
    VideoRenderer localRenderer;
    VideoRenderer remoteRenderer;

    Button hangup;
    PeerConnection localPeer;
    List<IceServer> iceServers;
    EglBase rootEglBase;

    VideoCapturer videoCapturerAndroid;

    boolean gotUserMedia;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    MediaStream stream;
    VideoTrack videoTrack;
    ExecutorService executor;

    private static final List<RendererCommon.ScalingType> scalingTypes = Arrays.asList(
            RendererCommon.ScalingType.SCALE_ASPECT_FIT, RendererCommon.ScalingType.SCALE_ASPECT_FILL,
            RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);

    /* time count */
    Thread thread = null;
    TextView timeCountTv;
    boolean isRunning = true;

    /* layout setting*/
    ConstraintLayout layout_video;
    ConstraintLayout layout_menu;

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getPermission();

        Intent intent = getIntent();
        String roomName = intent.getStringExtra("roomName");
        executor = Executors.newSingleThreadExecutor();

        /* UI */
        initViews();
        initVideos();

        /* 소켓 연결 */
        SignallingClient.getInstance().init(this, roomName);

        /* video 생성 */
        start();

        /* layout setting */
        layout_menu = (ConstraintLayout)findViewById(R.id.layout_menu);
        layout_video = (ConstraintLayout)findViewById(R.id.layout_video);
        layout_video.setOnTouchListener(this::onTouch);
        layout_menu.setOnTouchListener(this::onTouch);

        thread = new Thread(new timeThread());
        thread.start();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (v.getId()) {
            case R.id.layout_video:
                layout_menu.setBackgroundColor(Color.parseColor("#AA000000"));
                layout_menu.setVisibility(View.VISIBLE);
                return false;
            case R.id.layout_menu:
                layout_menu.setVisibility(View.INVISIBLE);
                layout_menu.setBackgroundColor(Color.parseColor("#00000000"));
                return false;
            default:
                return false;
        }
        // if you want to consume the behavior then return true else retur false
    }

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            // 시간 format :
            int mSec = msg.arg1 % 100;
            int sec = (msg.arg1 / 100) % 60;
            int min = (msg.arg1 /100) / 60;
            String minFormat = String.format("%02d", min);
            int hour = 0;
            if(minFormat.equals("59")){
                hour++;
            }
            String result = String.format("%02d:%02d:%02d",hour,min,sec);
            timeCountTv.setText(result);
        }
    };

    public class timeThread implements Runnable {
        @Override
        public void run() {
            int i = 0;

            while (true) {
                while (isRunning) { //일시정지를 누르면 멈추도록
                    Message msg = new Message();
                    msg.arg1 = i++;
                    handler.sendMessage(msg);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
//                        e.printStackTrace();
                        return; // 인터럽트 받을 경우 return됨
                    }
                }
            }
        }
    }

    /* 앱 permission 설정 메소드. */
    public void getPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int audioPermission = checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO);
            int videoPermission = checkCallingOrSelfPermission(Manifest.permission.CAMERA);
            int networkPermission = checkCallingPermission(Manifest.permission.ACCESS_NETWORK_STATE);

            if (audioPermission == getPackageManager().PERMISSION_DENIED
                    || videoPermission == getPackageManager().PERMISSION_DENIED
                    || networkPermission == getPackageManager().PERMISSION_DENIED) {

                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA, Manifest.permission.ACCESS_NETWORK_STATE}, 1000);
            }
        }
    }

    private void initViews() {
        hangup = findViewById(R.id.hangup_btn);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
        timeCountTv = findViewById(R.id.time_count_tv);
        hangup.setOnClickListener(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setZOrderMediaOverlay(true);
    }
//    HardwareVideoEncoderFactory hwEncoder;
//    HardwareVideoDecoderFactory hwDecoder;

    public void start() {

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.initializeAndroidGlobals(mContext, true);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
//        hwDecoder = new HardwareVideoDecoderFactory();
//        hwEncoder = new HardwareVideoEncoderFactory(true, true);
        peerConnectionFactory = new PeerConnectionFactory(options);
        //Now create a VideoCapturer instance.
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();
        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        /* 화면 해상도 구하기 */
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(height, width, 30);
        }

        localVideoView.setVisibility(View.VISIBLE);

        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = new VideoRenderer(localVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);

        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);

        gotUserMedia = true;

        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart();
        }
    }
    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            Log.e("여기?", "onTryToStart");
            Log.e("들어옵니까?", !SignallingClient.getInstance().isStarted+" : : : "+localVideoTrack.toString()+"::"+SignallingClient.getInstance().isChannelReady);
            if (!SignallingClient.getInstance().isStarted && localVideoTrack != null && SignallingClient.getInstance().isChannelReady) {
                Log.e("들어옵니까?", "::: in");
                createPeerConnection();
                SignallingClient.getInstance().isStarted = true;
                if (SignallingClient.getInstance().isInitiator) {
                    doCall();
                }
            }
        });
    }


    /**
     * Creating the local peerconnection instance
     */
    /*
     * 필요한 데이터.
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
//        rtcConfig.audioJitterBufferMaxPackets = 10;
//        rtcConfig.audioJitterBufferFastAccelerate = true;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new MediaConstraints(),new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });
        addStreamToLocalPeer();
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        stream  = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
            localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                    Log.d("onCreateSuccess", "SignallingClient emit ");
                    SignallingClient.getInstance().emitMessage(sessionDescription);
                }
            }, sdpConstraints);
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
//        //we have remote video stream. add to the renderer.
        executor.execute(() -> {
            if (peerConnectionFactory == null) {
                return;
            }
            if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                Log.e("Weird-looking stream: " ,""+stream);
                return;
            }
            if (stream.videoTracks.size() == 1) {
                remoteRenderer = new VideoRenderer(remoteVideoView);
                videoTrack = stream.videoTracks.get(0);
                remoteVideoView.setEnabled(true);
                videoTrack.addRenderer(remoteRenderer);
                stream.videoTracks.remove(0);
            }
        });
    }


    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    @Override
    public void onCreatedRoom() {
        showToast("You created the room " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media");
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom() {
        showToast("You joined the room " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media");
        }
    }

    @Override
    public void onNewPeerJoined() {
        showToast("Remote Peer Joined");
        //수정
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media");
        }
    }

    @Override
    public void onRemoteHangUp(String msg) {
        showToast("Remote Peer hungup");
        runOnUiThread(this::hangup);

    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {

            Log.e("여기?", "onOfferReceived");
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
                onTryToStart();
            }

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received Answer");
        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    ///체킹
    private void updateVideoViews(final boolean remoteVisible) {
        runOnUiThread(() -> {
            Log.e("여기?", "updateVBideoViews");
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(130);
                params.width = dpToPx(130);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });

    }

    /**
     * Closing up - normal hangup and app destroye
     */

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.talk_btn: {
                showToast("preparing...");
                break;
            }
            case R.id.hangup_btn: {
                hangup();
                break;
            }
            case R.id.document_btn: {
                showToast("preparing...");
                break;
            }
        }
    }
    private void hangup() {
        try {
            //쓰레드를 인터럽트해 멈춘다
            isRunning = false;
            thread.interrupt();
            timeCountTv.setText("00:00:00");

            /* localPeer 종료 */
            if (localPeer == null) {
            } else {
                SignallingClient.getInstance().isStarted = false;
                SignallingClient.getInstance().isChannelReady = false;
                SignallingClient.getInstance().isInitiator = false;
// SignallingClient.getInstance().close();-> onDestroy 에서 수행.
                localPeer.dispose();
                sdpConstraints = null;
            }

            /* remotePeer 종료 */
            Log.d("행업", "555");
            if (remoteVideoView != null) {
// remoteVideoView.removeCallbacks(runOnUiThread());
                remoteVideoView.release();
                remoteVideoView = null;
            }
            Log.d("행업", "6666");
            if (remoteRenderer != null) {
                remoteRenderer.dispose();
            }

            /* localVideo 종료 */
            localVideoTrack.removeRenderer(localRenderer);
            Log.d("행업", "what");
            if(localVideoView != null) {
                localVideoView.release();
            }
            if(localRenderer != null){
                localRenderer.dispose();
            }

            if(videoCapturerAndroid != null) {
                videoCapturerAndroid.dispose();

            }
            if(peerConnectionFactory != null) {
                peerConnectionFactory.dispose();
            }
// rootEglBase.detachCurrent(); //peer2가 먼저 나갔을 때 에러
            if(rootEglBase != null) {
                rootEglBase.release();
            }

            if(videoConstraints != null){
                videoConstraints = null;
            }
            if(audioConstraints != null){
                audioConstraints = null;
            }

            /* 화면 전환 */
            Intent off = new Intent(getApplicationContext(), StartActivity.class);
            startActivity(off);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        SignallingClient.getInstance().close();
        super.onDestroy();
    }

    /**
     * Util Methods
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

}