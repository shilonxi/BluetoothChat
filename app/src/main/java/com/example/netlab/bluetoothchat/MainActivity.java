package com.example.netlab.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static String mRevMsg;  //接收到的对方发送过来的聊天消息
    private static Handler mHandler = new Handler();//Handler消息传递机制
    private static TextView  mtvRemDCState;
    private static ListView mtvRevMsg;
    public static List<String > list = null;      //显示列表
    public static ArrayAdapter<String> adapter = null;   //listView适配
    private EditText medSendMsg;
    private Button mbtnSend, mbtnCheck;
    private Spinner mspPairedDevices;   //显示已配对的蓝牙设备信息
    //已配对的蓝牙设备信息列表
    private List<String> mlPairedDevices = new ArrayList<String>();
    //蓝牙设备上的标准串行
    private static final UUID MY_UUID = UUID.fromString("00011101-0000-1000-807-000805F9B34FB");
    //己方的蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter = null;
    //己方聊天信息到达的对方蓝牙设备
    private BluetoothDevice mSendtoDevice = null;
    //用于发送信息的蓝牙套接字
    private BluetoothSocket mSendSocket = null;
    //服务监听线程
    private AcceptThread mAcceptThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mtvRemDCState = (TextView)findViewById(R.id.RemDCState);
        mtvRevMsg = (ListView)findViewById(R.id.RevMsg);
        list = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,list);
        mtvRevMsg.setAdapter(adapter);
        medSendMsg = (EditText)findViewById(R.id.edittext);
        mbtnCheck= (Button) findViewById(R.id.btnCheck);
        mbtnSend= (Button) findViewById(R.id.btnSend);
        mspPairedDevices = (Spinner)findViewById(R.id.simple_spinner_item);

        mbtnCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获得BluetoothAdapter对象
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if(mBluetoothAdapter == null){
                    mtvRemDCState.setText("本机没有蓝牙设备，本程序无法运行！");
                    return;
                }else if(! mBluetoothAdapter.isEnabled()){
                    mtvRemDCState.setText("本机蓝牙没有开启，请开启蓝牙！");
                    return;
                }
                //获得已配对的远程蓝牙设备的集合
                Set<BluetoothDevice>pairedDevices = mBluetoothAdapter.getBondedDevices();
                if(pairedDevices.size() <= 0){
                    mtvRemDCState.setText("还没有已配对的远程蓝牙设备，请先配对！");
                    return;
                }else {
                    mlPairedDevices.clear();
                    for (Iterator<BluetoothDevice> it = pairedDevices.iterator();it.hasNext();)
                    {
                        BluetoothDevice pairdDecive = (BluetoothDevice)it.next();
                        //保存已配对的远程蓝牙设备的名字
                        mlPairedDevices.add(pairdDecive.getName());
                    }
                    //初始化Spinner控件
                    ArrayAdapter<String>adapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_spinner_item,mlPairedDevices);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    mspPairedDevices.setAdapter(adapter);
                    mtvRemDCState.setText("已配对设备: ");
                    if(mAcceptThread == null){
                        //开启作为服务器的接收线程
                        mAcceptThread = new AcceptThread();
                        mAcceptThread.start();
                    }else if(!mAcceptThread.isAlive()){
                        mAcceptThread.start();
                    }
                }
            }
        });
        mspPairedDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled())
                {
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        //获得选中的子项的内容（字符串）
                        String selName = mspPairedDevices.getItemAtPosition(arg2).toString();
                        //遍历已配对蓝牙设备，找出选中名字的蓝牙设备作为通信的远程聊天对象
                        for (Iterator<BluetoothDevice> it = pairedDevices.iterator(); it.hasNext(); )
                        {
                            BluetoothDevice pairedDevice = (BluetoothDevice)it.next();
                            if (selName.equals(pairedDevice.getName())) {
                                mSendtoDevice = pairedDevice;
                                mtvRemDCState.setText("聊天对象");
                                break;
                            }
                        }
                    } else {
                        mtvRemDCState.setText("蓝牙丢失配对项，请重新检查蓝牙！");
                    }
                } else{ mtvRemDCState.setText("蓝牙没有启动！");}
            }
            //  @Override
            public void onNothingSelected(AdapterView<?>arg0){
                //TODO Auto- generated method stub
            }
        });

        mbtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled() || mSendtoDevice == null) {
                    mtvRemDCState.setText("请先检查蓝牙，并选择要连接的远程蓝牙设备！");
                    return;
                }
                if (mSendSocket == null) {
                    //还未和远程聊天设备建立连接，先连接到要聊天的远程设备
                    if (!ConnectRemoteDevice()) {
                        mtvRemDCState.setText("未能和远程设备建立连接！");
                        return;
                    }
                }
                WritetoRemoteDevice(medSendMsg.getText().toString());
                medSendMsg.setText("");
            }
        });
    }

    //作为服务器的接收连接的线程
    private class AcceptThread extends Thread
    {
        //创建BluetoothServerSocket类
        public final String NAME_SECURE = "MY_SECURE";
        private  BluetoothServerSocket mmServerSocket = null;
        ReceiveMsgThread comThread = null;  //数据传输线程
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                //MY_UUID时应用的UUID标识
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID);
            } catch (IOException e) {}
            mmServerSocket = tmp;
        }
        //线程启动时运行
        public void run()
        {
            //连接进来的客户端
            BluetoothSocket revSocket = null;
            //保持侦听
            while(true)
            {
                try
                {
                    //接受连接
                    revSocket = mmServerSocket.accept();
                }catch (IOException e){break;}
                //连接被接受
                if(revSocket != null)
                {
                    //启动数据传输线程
                    comThread = new ReceiveMsgThread(revSocket);
                    comThread.start(); //启动线程
                    //关闭连接，由于每个RECOMM通道一次只允许连接一个客户端
                    //而mmServerSocket获得连接后与RFCOMM通道绑定
                    //故而大多数情况下在接收到一个连接套接字后将mmServerSocket关闭
                    cancel();
                    break;
                }
            }
        }
        //关闭连接
        public void cancel()
        {
            try {
                //关闭BluetoothServerSocket，该操作不会关闭被连接的已有accept（）方法所返回的BluetoothSocket对象
                mmServerSocket.close();
            }catch (IOException e){}
        }
        @Override
        public void destroy()
        {
            //关闭连接
            cancel();
        }

    }

    private boolean ConnectRemoteDevice()
    {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mSendtoDevice != null)
        {
            BluetoothSocket tmp = null;
            try{
                //根据UUID（全球唯一标识符）创建并返回一个BluetoothSocket
                tmp = mSendtoDevice.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){return false;}
            //赋值给BluetoothSocket
            mSendSocket = tmp;
            //取消搜索设备，确保连接成功
            mBluetoothAdapter.cancelDiscovery();
            try {
                //连接到设备
                mSendSocket.connect();
            }catch (IOException e)
            {
                try {
                    mSendSocket.close();
                    return false;
                }catch (IOException p){}

            }
            return true;
        }
        return false;

    }
    public void WritetoRemoteDevice(String sendMsg)
    {
        if(mSendSocket != null)
        {
            byte[] sendBytes = sendMsg.getBytes(Charset.forName("UTF-8"));
            try{
                OutputStream outputStream = mSendSocket.getOutputStream();
                //写数据到输出流中
                outputStream.write(sendBytes);
            }catch (IOException e){}
        }
    }
    //作为服务器接收远程设备的数据，该线程实现如下
    private class ReceiveMsgThread extends Thread
    {
        private boolean mIsStop = false;
        //BluetoothSocket对象
        private  BluetoothSocket mmSocket = null;
        //输入流对象
        private  InputStream mmInStream = null;
        public ReceiveMsgThread(BluetoothSocket socket)
        {
            //为BluetoothSocket赋初值
            mmSocket = socket;
            //输入流赋值为null
            InputStream tmpIn = null;
            try{
                //从BluetoothSocket中获取输入流
                tmpIn = socket.getInputStream();
            }catch (IOException e){}
            //为输入流赋值
            mmInStream = tmpIn;
        }
        public void run()
        {
            //保持监听以便随时读取
            while (!mIsStop)
            {
                try {
                    //从输入流中读取数据
                    int count = 0;
                    while (count == 0)
                        count = mmInStream.available();
                    byte[] buffer = new byte[count]; //流的缓冲区大小
                    int tmp = mmInStream.read(buffer,0,buffer.length);
                    if(tmp == -1) continue;
                    //将接收的字节流编码成字符串
                    String revMsg = new String(buffer,Charset.forName("UTF-8"));
                    UpdateRecMsg(revMsg);
                    //当对方发送Over字符串时结束该通信线程，即mIsStop = true
                    if(revMsg.equals("Over")){
                        revMsg = "通话结束";
                        UpdateRecMsg(revMsg);
                        mIsStop = true;
                    }
                }catch (IOException e){break;}
            }
        }
        //取消
        public void cancel()
        {
            try {
                //关闭连接
                mmSocket.close();
            }catch (IOException e){}
        }
        @Override
        public void destroy(){
            cancel();
        }
    }

    //更新接收到的对方聊天信息
    public  void UpdateRecMsg(String revMsg) {
        mRevMsg = revMsg;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                list.add(mRevMsg);
                adapter.notifyDataSetChanged();
            }
        });
    }

}



