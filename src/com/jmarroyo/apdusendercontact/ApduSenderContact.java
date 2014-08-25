 /*
Copyright 2014  Jose Maria ARROYO jm.arroyo.castejon@gmail.com

APDUSenderContact is free software: you can redistribute it and/or modify
it  under  the  terms  of the GNU General Public License  as published by the
Free Software Foundation, either version 3 of the License, or (at your option)
any later version.

APDUSenderContact is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/


package com.jmarroyo.apdusendercontact;


import java.util.Arrays;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.acs.smartcard.Features;
import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;
import com.acs.smartcard.ReaderException;


public class ApduSenderContact extends Activity
{
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    int iActualState = 1;
    private UsbManager mManager;
    private static Reader mReader;
    private PendingIntent mPermissionIntent;
    static int iSlotNum = -1;
    private String deviceName;

    private Features mFeatures = new Features();
    static HexadecimalKbd mHexKbd;

    static TextView TextUsb;
    static TextView TextCard;

    static ImageView icoUSB;
    static ImageView icoCard;

    static byte[] byteAPDU=null;
    static byte[] respAPDU=null;

    static byte[] byteAPDU2=null;
    static byte[] respAPDU2=null;

    private static CheckBox mCheckRaw;
    private static CheckBox mCheckResp;
    private static CheckBox mCheckAutoSend;

    private Button mSendAPDUButton;
    private Button mClearLogButton;
    private Button mPasteButton;

    static TextView txtCLA;
    static TextView txtINS;
    static TextView txtP1;
    static TextView txtP2;
    static TextView txtLc;
    static TextView txtDataIn;
    static TextView txtLe;

    static EditText editCLA;
    static EditText editINS;
    static EditText editP1;
    static EditText editP2;
    static EditText editLc;
    static EditText editDataIn;
    static EditText editLe;

    static TextView txtLog;
    
    private Spinner mCommandsSpinner;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                   
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                        if (device != null)
                        {
                            new OpenTask().execute(device);
                        }
                    }
                    else
                    {
                        icoUSB.setImageResource(R.drawable.ic_usb_off);
                        TextUsb.setText("NO READER DETECTED");
                        TextCard.setText("PLEASE INSERT CARD");
                        icoCard.setImageResource(R.drawable.ic_icc_off);
                        clearlog();
                        mSendAPDUButton.setEnabled(false);
                        print("     Permission for USB hardware");
                        print("          has been DENIED.      ");
                        print("         Please allow it first. ");
                    }
                }
            }
            else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
            {
                synchronized (this)
                {
                    deviceName="";
                    for(UsbDevice device : mManager.getDeviceList().values())
                    {
                        if(mReader.isSupported(device))
                        {
                            deviceName = device.getDeviceName();
                            break;
                        }
                    }
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && device.equals(mReader.getDevice()))
                    {
                        icoUSB.setImageResource(R.drawable.ic_usb_off);
                        TextUsb.setText("NO READER DETECTED");
                        TextCard.setText("PLEASE INSERT CARD");
                        icoCard.setImageResource(R.drawable.ic_icc_off);
                        clearlog();
                        mSendAPDUButton.setEnabled(false);
                        
                        mReader.close();
                    }
                }
            }
        }
    };
    
    private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> 
    {
        @Override
        protected Exception doInBackground(UsbDevice... params) 
        {
            Exception result = null;
            try 
            {
                mReader.open(params[0]);
            } 
            catch (Exception e) 
            {
                result = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception result) 
        {
            if (result != null) 
            {
                clearlog();
                print("Please, disconnect and reconnect the reader again.");
            } 
            else 
            {
              icoUSB.setImageResource(R.drawable.ic_usb_on);
              TextUsb.setText(mReader.getReaderName());
              int numSlots = mReader.getNumSlots();
              if(numSlots>0)
              {
                  iSlotNum=0;
              }
              mFeatures.clear();
              clearlog();
              print("This program is distributed in the hope that it will be useful for educational purposes.  Enjoy! ");
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
                
        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mReader = new Reader(mManager);
        mReader.setOnStateChangeListener(new OnStateChangeListener()
        {
            @Override
            public void onStateChange(int slotNum, int prevState, int currState)
            {
                if (prevState < Reader.CARD_UNKNOWN || prevState > Reader.CARD_SPECIFIC)
                {
                    prevState = Reader.CARD_UNKNOWN;
                }
                if (currState < Reader.CARD_UNKNOWN || currState > Reader.CARD_SPECIFIC)
                {
                    currState = Reader.CARD_UNKNOWN;
                }
                final int iActualState = currState;

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(iActualState==1)
                        {
                            
                            clearlog();
                            icoCard.setImageResource(R.drawable.ic_icc_off);
                            TextCard.setText("PLEASE INSERT CARD");
                            editCLA.setText("");
                            editINS.setText("");
                            editP1.setText("");
                            editP2.setText("");
                            editLc.setText("");
                            editLe.setText("");
                            editDataIn.setText("");
                            editCLA.requestFocus();
                            mSendAPDUButton.setEnabled(false);
                            
                        }
                        if(iActualState==2)
                        {
                            clearlog();
                            vPowerOnCard();
                        }
                    }
                });
            }
        });

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);
        
        
        txtLog = (TextView) findViewById(R.id.textLog);

        deviceName="";
        for(UsbDevice device : mManager.getDeviceList().values())
        {
            if (mReader.isSupported(device))
            {
                deviceName = device.getDeviceName();
                break;
            }
        }

        byteAPDU=null;
        byteAPDU2=null;
        respAPDU=null;
        respAPDU2=null;

        mHexKbd= new HexadecimalKbd(this, R.id.keyboardview, R.xml.hexkbd);
        mHexKbd.registerEditText(R.id.editCLA);
        mHexKbd.registerEditText(R.id.editINS);
        mHexKbd.registerEditText(R.id.editP1);
        mHexKbd.registerEditText(R.id.editP2);
        mHexKbd.registerEditText(R.id.editLc);
        mHexKbd.registerEditText(R.id.editDataIn);
        mHexKbd.registerEditText(R.id.editLe);
        
        icoUSB = (ImageView) findViewById(R.id.imageUsb);
        icoUSB.setImageResource(R.drawable.ic_usb_off);

        icoCard = (ImageView) findViewById(R.id.imageCard);
        icoCard.setImageResource(R.drawable.ic_icc_off);

        TextUsb = (TextView) findViewById(R.id.textUsb);
        TextCard = (TextView) findViewById(R.id.textCard);

        txtCLA = (TextView) findViewById(R.id.textCLA);
        txtINS = (TextView) findViewById(R.id.textINS);
        txtP1 = (TextView) findViewById(R.id.textP1);
        txtP2 = (TextView) findViewById(R.id.textP2);
        txtLc = (TextView) findViewById(R.id.textLc);
        txtDataIn = (TextView) findViewById(R.id.textDataIn);
        txtLe = (TextView) findViewById(R.id.textLe);

        editCLA = (EditText) findViewById(R.id.editCLA);
        editINS = (EditText) findViewById(R.id.editINS);
        editP1 = (EditText) findViewById(R.id.editP1);
        editP2 = (EditText) findViewById(R.id.editP2);
        editLc = (EditText) findViewById(R.id.editLc);
        editDataIn = (EditText) findViewById(R.id.editDataIn);
        editLe = (EditText) findViewById(R.id.editLe);

        mSendAPDUButton = (Button) findViewById(R.id.button_SendApdu);
        mSendAPDUButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clearlog();
                if(!bSendAPDU())
                {
                    vShowErrorVaules();
                }
            }
        });

        mClearLogButton = (Button) findViewById(R.id.button_ClearLog);
        mClearLogButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) 
            {
                clearlog();
            }
        });
        
        
        mPasteButton = (Button) findViewById(R.id.button_Paste);
        mPasteButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) 
            {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
                String ClipBoardData = clipboard.getText().toString().toUpperCase();
                
                if(ClipBoardData.length() > 254)
                {
                    vShowGeneralMesg("Max Length to Paste is 254 chars !");
                }
                else if(ClipBoardData.length() >= 8)
                {
                    if( (ClipBoardData.length()%2)!=0)
                    {
                        vShowGeneralMesg("String Length must be Even !");
                    }
                    if (!ClipBoardData.matches("^[0-9A-F]+$"))
                    {
                        clearlog();
                        print(ClipBoardData);
                        vShowGeneralMesg("String should be '0'-'9' or 'A'-'F'");
                    }
                    else
                    {
                        vSetBuiltinCommand();
                        editDataIn.setText(ClipBoardData);
                        HideKbd();
                        vShowGeneralMesg("Data Pasted Successfully");
                    }
                }
                else
                {
                    vShowGeneralMesg("Length must be greater than 8 chars !");
                }
            }
        });

        mCheckRaw=(CheckBox)findViewById(R.id.check_box_raw);
        mCheckRaw.setOnCheckedChangeListener(new OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if(isChecked)
                {
                    editCLA.setText("");
                    editINS.setText("");
                    editP1.setText("");
                    editP2.setText("");
                    editLc.setText("");
                    editLe.setText("");
                    editDataIn.setText("");

                    txtCLA.setEnabled(false);
                    txtINS.setEnabled(false);
                    txtP1.setEnabled(false);
                    txtP2.setEnabled(false);
                    txtLc.setEnabled(false);
                    txtLe.setEnabled(false);
                    editCLA.setEnabled(false);
                    editINS.setEnabled(false);
                    editP1.setEnabled(false);
                    editP2.setEnabled(false);
                    editLc.setEnabled(false);
                    editLe.setEnabled(false);
                    editDataIn.setEnabled(true);
                    txtDataIn.setEnabled(true);
                    editDataIn.requestFocus();
                    txtDataIn.setText("APDU:");
                }
                else
                {
                    editCLA.setText("");
                    editINS.setText("");
                    editP1.setText("");
                    editP2.setText("");
                    editLc.setText("");
                    editLe.setText("");
                    editDataIn.setText("");

                    txtCLA.setEnabled(true);
                    txtINS.setEnabled(true);
                    txtP1.setEnabled(true);
                    txtP2.setEnabled(true);
                    txtLc.setEnabled(true);
                    txtDataIn.setEnabled(true);
                    txtLe.setEnabled(true);
                    txtLe.setEnabled(true);
                    editCLA.setEnabled(true);
                    editINS.setEnabled(true);
                    editP1.setEnabled(true);
                    editP2.setEnabled(true);
                    editLc.setEnabled(true);
                    editDataIn.setEnabled(true);
                    txtDataIn.setText("Data:");
                    editLe.setEnabled(true);
                    mCommandsSpinner.setSelection(0);
                    editCLA.requestFocus();
                }
                
            }
        });

        mCheckResp = (CheckBox) findViewById(R.id.check_box_resp);
        mCheckResp.setOnCheckedChangeListener(new OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
               
                if( (byteAPDU==null)||(respAPDU==null) )
                {
                    return;
                }
                if( isChecked )
                {
                    clearlog();
                    print("***COMMAND APDU***");
                    print("");
                    try
                    {
                        print("IFD - " + getHexString(byteAPDU,byteAPDU.length));
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    if( (byteAPDU2!=null)&&(respAPDU2!=null) )
                    {
                        try
                        {
                            print("");
                            print("ICC - " + getHexString(respAPDU2,respAPDU2.length));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        try
                        {
                            print("IFD - " + getHexString(byteAPDU2,byteAPDU2.length));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        
                    }
                    try
                    {
                        print("");
                        print("ICC - " + getHexString(respAPDU,respAPDU.length));
                    }
                    catch (Exception e) 
                    {
                        e.printStackTrace();
                    }
                    
                    try
                    {
                        vShowResponseInterpretation(respAPDU);
                    }
                    catch (Exception e) 
                    {
                        clearlog();
                        print("Response is not TLV format !!!");
                    }
                }
                else
                {
                    clearlog();
                    print("***COMMAND APDU***");
                    print("");
                    try 
                    {
                        print("IFD - " + getHexString(byteAPDU,byteAPDU.length));
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    if( (byteAPDU2!=null)&&(respAPDU2!=null) )
                    {
                        try
                        {
                            print("");
                            print("ICC - " + getHexString(respAPDU2,respAPDU2.length));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        try
                        {
                            print("IFD - " + getHexString(byteAPDU2,byteAPDU2.length));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        
                    }
                    try
                    {
                        print("");
                        print("ICC - " + getHexString(respAPDU,respAPDU.length));
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });

        mCheckAutoSend = (CheckBox) findViewById(R.id.check_box_autosendt0);

        final String[] commadsTableNames = { 
            "Built-in APDUs...",
            "SELECT PSE", 
            "SELECT PPSE",
            "SELECT VISA AID",
            "SELECT VISA ELECTRON AID",
            "SELECT MASTERCARD AID",
            "SELECT AMEX AID",
            "SELECT DINERS/DISCOVER AID",
            "SELECT INTERAC AID",
            "SELECT CUP AID",
            "READ RECORD SFI:01 R:01",
            "READ RECORD SFI:01 R:02",
            "READ RECORD SFI:02 R:01",
            "READ RECORD SFI:02 R:02",
            "GET ATC",
            "GET LAST ONLINE ATC",
            "GET PIN TRY COUNTER"
            };
        ArrayAdapter<String> commadsTable = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, commadsTableNames);
        mCommandsSpinner = (Spinner) findViewById(R.id.APDU_spinner_table);
        mCommandsSpinner.setAdapter(commadsTable);
        mCommandsSpinner.setSelection(0);
        mCommandsSpinner.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,int arg2, long arg3)
            {
                
                int CommandAPDU = mCommandsSpinner.getSelectedItemPosition();
                switch (CommandAPDU)
                {
                    case 0:
                    	
                    mCheckRaw.setChecked(false);
                    editCLA.setText("");
                    editINS.setText("");
                    editP1.setText("");
                    editP2.setText("");
                    editLc.setText("");
                    editLe.setText("");
                    editDataIn.setText("");

                    txtCLA.setEnabled(true);
                    txtINS.setEnabled(true);
                    txtP1.setEnabled(true);
                    txtP2.setEnabled(true);
                    txtLc.setEnabled(true);
                    txtDataIn.setEnabled(true);
                    txtLe.setEnabled(true);
                    txtLe.setEnabled(true);
                    editCLA.setEnabled(true);
                    editINS.setEnabled(true);
                    editP1.setEnabled(true);
                    editP2.setEnabled(true);
                    editLc.setEnabled(true);
                    editDataIn.setEnabled(true);
                    txtDataIn.setText("Data:");
                    editLe.setEnabled(true);
                    editCLA.requestFocus();
                    
                    break;
                    case 1: //SELECT PSE
                        vSetBuiltinCommand();
                        editDataIn.setText("00A404000E315041592E5359532E4444463031");
                        HideKbd();
                        vShowGeneralMesg("Payment System Environment");
                    break;
                    case 2: //SELECT PPSE
                        vSetBuiltinCommand();
                        editDataIn.setText("00A404000E325041592E5359532E4444463031");
                        HideKbd();
                        vShowGeneralMesg("Proximity Payment System Environment");
                    break;
                    case 3: //SELECT VISA AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040007A0000000031010");
                        HideKbd();
                        vShowGeneralMesg("Visa credit or debit");
                    break;
                    case 4: //SELECT VISA ELECTRON AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040007A0000000032010");
                        HideKbd();
                        vShowGeneralMesg("Visa Electron");
                    break;
                    case 5: //SELECT MASTERCARD AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040007A0000000041010");
                        HideKbd();
                        vShowGeneralMesg("MasterCard credit or debit");
                    break;
                    case 6: //SELECT AMEX AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040006A00000002501");
                        HideKbd();
                        vShowGeneralMesg("American Express");
                    break;
                    case 7: //SELECT DINERS/DISCOVER AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040007A0000001523010");
                        HideKbd();
                        vShowGeneralMesg("Diners Club/Discover");
                    break;
                    case 8: //SELECT INTERAC AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040007A0000002771010");
                        HideKbd();
                        vShowGeneralMesg("Interac Debit card");
                    break;
                    case 9: //SELECT CUP AID
                        vSetBuiltinCommand();
                        editDataIn.setText("00A4040008A000000333010101");
                        HideKbd();
                        vShowGeneralMesg("UnionPay Debit");
                    break;
                    case 10: //READRECORD SFI:01 R:01
                        vSetBuiltinCommand();
                        editDataIn.setText("00B2010C00");
                        HideKbd();
                        vShowGeneralMesg("SFI:01 R:01");
                    break;
                    case 11: //READRECORD SFI:01 R:02
                        vSetBuiltinCommand();
                        editDataIn.setText("00B2020C00");
                        HideKbd();
                        vShowGeneralMesg("SFI:01 R:02");
                    break;
                    case 12: //READRECORD SFI:02 R:01
                        vSetBuiltinCommand();
                        editDataIn.setText("00B2011400");
                        HideKbd();
                        vShowGeneralMesg("SFI:02 R:01");
                    break;
                    case 13: //READRECORD SFI:02 R:02
                        vSetBuiltinCommand();
                        editDataIn.setText("00B2021400");
                        HideKbd();
                        vShowGeneralMesg("SFI:02 R:02");
                    break;
                    case 14: //GET ATC
                        vSetBuiltinCommand();
                        editDataIn.setText("80CA9F3600");
                        HideKbd();
                        vShowGeneralMesg("Get Tag 9F36");
                    break;
                    case 15: //GET LAST ONLINE ATC
                        vSetBuiltinCommand();
                        editDataIn.setText("80CA9F1300");
                        HideKbd();
                        vShowGeneralMesg("Get Tag 9F13");
                    break;
                    case 16: //GET PIN TRY COUNTER
                        vSetBuiltinCommand();
                        editDataIn.setText("80CA9F1700");
                        HideKbd();
                        vShowGeneralMesg("Get Tag 9F17");
                    break;
                    
                    
                    default:
                    break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
                return;
            }
        });

        mSendAPDUButton.setEnabled(false);
        mClearLogButton.setEnabled(true);
        
        mCommandsSpinner.setEnabled(true);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mSendAPDUButton.setEnabled(false);
        clearlog();
        print("     Please Start the application");
        print("        with the USB-OTG reader  ");
        print("                CONNECTED        ");

        if(deviceName!="")
        {
            for (UsbDevice device : mManager.getDeviceList().values())
            {
                if (deviceName.equals(device.getDeviceName()))
                {
                    clearlog();
                    mManager.requestPermission(device,mPermissionIntent);
                    break;
                }
            }
        }

    }

    @Override
    public void onDestroy()
    {
        mReader.close();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    
    @Override 
    public void onBackPressed()
    {
        if( mHexKbd.isCustomKeyboardVisible() ) mHexKbd.hideCustomKeyboard(); else this.finish();
    }
    
    

    private static void HideKbd()
    {
        if( mHexKbd.isCustomKeyboardVisible() ) mHexKbd.hideCustomKeyboard();
    }

    

    private void vPowerOnCard()
    {
        byte[] atr = null;
        int actionNum = 1;
        int preferredProtocols = (Reader.PROTOCOL_UNDEFINED | Reader.PROTOCOL_T0 | Reader.PROTOCOL_T1);
        int activeProtocol = 0;
        
        if (iSlotNum >= 0)
        {
            if(actionNum < Reader.CARD_POWER_DOWN || actionNum > Reader.CARD_WARM_RESET)
            {
                actionNum = Reader.CARD_WARM_RESET;
            }
            try 
            {
                atr = mReader.power(iSlotNum, actionNum);
            }
            catch (ReaderException e1)
            {
                e1.printStackTrace();
            }

            if(atr != null)
            {
                try
                {
                    TextCard.setText(getHexString(atr, atr.length));
                    icoCard.setImageResource(R.drawable.ic_icc_on);
                    mSendAPDUButton.setEnabled(true);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            try
            {
                activeProtocol = mReader.setProtocol(iSlotNum,preferredProtocols);
            }
            catch (ReaderException e)
            {
                e.printStackTrace();
            }
            String activeProtocolString = "Transmission Protocol ";
            switch (activeProtocol)
            {
                case Reader.PROTOCOL_T0:
                    activeProtocolString += "T=0";
                break;
                case Reader.PROTOCOL_T1:
                    activeProtocolString += "T=1";
                break;
                default:
                    activeProtocolString += "Unknown";
                break;
            }
            vShowCardProtocol(activeProtocolString);
        }
    }

    private void vSetBuiltinCommand()
    {
    	clearlog();
    	
        editCLA.setText("");
        editINS.setText("");
        editP1.setText("");
        editP2.setText("");
        editLc.setText("");
        editLe.setText("");
        editDataIn.setText("");

        txtCLA.setEnabled(false);
        txtINS.setEnabled(false);
        txtP1.setEnabled(false);
        txtP2.setEnabled(false);
        txtLc.setEnabled(false);
        txtLe.setEnabled(false);
        editCLA.setEnabled(false);
        editINS.setEnabled(false);
        editP1.setEnabled(false);
        editP2.setEnabled(false);
        editLc.setEnabled(false);
        editLe.setEnabled(false);
        editDataIn.setEnabled(true);
        txtDataIn.setEnabled(true);
        txtDataIn.setText("APDU:");
        mCheckRaw.setChecked(true);
               
        return;
    }
                    
    private static void vShowResponseInterpretation(byte[] data)
    {
        print("");
        print("====================================");
        print("RESPONSE INTERPRETATION:");

        if (data.length > 2)
        {
            byte[] sw12 = new byte[2];
            System.arraycopy(data, data.length-2, sw12, 0, 2);
            byte[] payload = Arrays.copyOf(data, (data.length)-2 );
            try
            {
                print("SW1-SW2 " + getHexString(sw12,sw12.length) + RetStatusWord.getSWDescription(Util.szByteHex2String(sw12[0]) + Util.szByteHex2String(sw12[1])));
            }
            catch (Exception e)
            {
                print("Error Processing Response");
            }
            print(EmvInterpreter.ShowEMV_Interpretation(payload));
            

        }
        else if (data.length == 2)
        {
            byte[] sw12 = new byte[2];
            System.arraycopy(data, data.length-2, sw12, 0, 2);
            try
            {
                print("SW1-SW2 " + getHexString(sw12,sw12.length) );
                print(RetStatusWord.getSWDescription(Util.szByteHex2String(sw12[0]) + Util.szByteHex2String(sw12[1])));
            }
            catch (Exception e)
            {
                print("Error Processing Response");
            }
        }
        print("====================================");
        return;
    }

    private void vShowCardProtocol(String activeProtocolString)
    {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;
        HideKbd();
        Toast toast = Toast.makeText(context, activeProtocolString, duration);
        toast.show();
    }
    
    private void vShowGeneralMesg(String szText)
    {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, szText, duration);
        toast.show();
    }

    private static  byte[]  transceives (byte[] data)
    {
        byte[] response = new byte[512];
        int responseLength = 0;
        try
        {
            print("***COMMAND APDU***");
            print("");
            print("IFD - " + getHexString(data,data.length));
        }
        catch (Exception e1)
        {
            e1.printStackTrace();
        }

        if (iSlotNum >= 0)
        {
            try
            {
                responseLength = mReader.transmit(iSlotNum,data, data.length, response,response.length);
            }
            catch (Exception e)
            {
                print("****************************************");
                print("       ERROR transmit: Review APDU  ");
                print("****************************************");
                responseLength=0;
                byte[] ra = Arrays.copyOf(response, responseLength);
                response = null;
                return (ra);
            }
            try
            {
                print("");
                print("ICC - " + getHexString(response,responseLength));
                byte[] ra2 = Arrays.copyOf(response, responseLength);
                respAPDU2=ra2;
                
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        if(mCheckAutoSend.isChecked())
        {
            if( (response[0]==0x61)||(response[0]==0x6C) )
            {
                byte[] GetResponse = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
                if(response[0]==0x6C)
                {
                    GetResponse[0]=data[0];
                    GetResponse[1]=data[1];
                    GetResponse[2]=data[2];
                    GetResponse[3]=data[3];
                    GetResponse[4]=response[1];
                }
                else
                {
                    GetResponse[0]=(byte)0x00;
                    GetResponse[1]=(byte)0xC0;
                    GetResponse[2]=(byte)0x00;
                    GetResponse[3]=(byte)0x00;
                    GetResponse[4]=response[1];
                }
                try
                {
                    print("IFD - " + getHexString(GetResponse,GetResponse.length));
                    byteAPDU2=GetResponse;
                }
                catch (Exception e1) 
                {
                    e1.printStackTrace();
                }
                if(iSlotNum >= 0)
                {
                    try
                    {
                        responseLength = mReader.transmit(iSlotNum,GetResponse, GetResponse.length, response,response.length);
                    }
                    catch (Exception e)
                    {
                        print("****************************************");
                        print("       ERROR transmit: Review APDU  ");
                        print("****************************************");
                        responseLength=0;
                        byte[] ra = Arrays.copyOf(response, responseLength);
                        response = null;
                        return (ra);
                    }
                    try
                    {
                        print("");
                        print("ICC - " + getHexString(response,responseLength));
                       
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            else
            {
                respAPDU2=null;
                byteAPDU2=null;
            }
        }
        byte[] ra = Arrays.copyOf(response, responseLength);
        response = null;
        return (ra);
    }

    private static boolean bSendAPDU() 
    {
        HideKbd();
        
        byteAPDU=null;
        byteAPDU2=null;
        respAPDU=null;
        respAPDU2=null;
        
        String StringAPDU = null;

        String StringCLA = editCLA.getText().toString();
        String StringINS = editINS.getText().toString();
        String StringP1 = editP1.getText().toString();
        String StringP2 = editP2.getText().toString();
        String StringLc = editLc.getText().toString();
        String StringDataIn = editDataIn.getText().toString();
        String StringLe = editLe.getText().toString();

        if (!mCheckRaw.isChecked())
        {
            if ( (StringCLA.length()==0)||(StringINS.length()==0)||(StringP1.length()==0)||(StringP2.length()==0)||( (StringDataIn.length()%2)!=0 ) )
            {
                return false;
            }
            if(!StringLc.contentEquals(""))
            {
                if( StringDataIn.length() != (((int) Long.parseLong(StringLc, 16))*2) )
                {
                    return false;
                }
            }
            if ( StringLe.length() == 1 )
            {
                StringLe = "0"+ StringLe;
                editLe.setText(StringLe);
            }
            if ( StringLc.length() == 1 )
            {
                StringLc = "0"+ StringLc;
                editLc.setText(StringLc);
            }
            if ( StringP2.length() == 1 )
            {
                StringP2 = "0"+ StringP2;
                editP2.setText(StringP2);
            }
            if ( StringP1.length() == 1 )
            {
                StringP1 = "0"+ StringP1;
                editP1.setText(StringP1);
            }
            if ( StringINS.length() == 1 )
            {
                StringINS = "0"+ StringINS;
                editINS.setText(StringINS);
            }
            if ( StringCLA.length() == 1 )
            {
            StringCLA = "0"+ StringCLA;
            editCLA.setText(StringCLA);
            }
        }

        if (mCheckRaw.isChecked())
        {
            StringAPDU = editDataIn.getText().toString();
            if ( ((StringAPDU.length()%2)!=0)|| (StringAPDU.length() < 1) )
            {
                return false;
            }
        }
        else
        {
            StringAPDU = StringCLA + StringINS + StringP1 + StringP2 + StringLc + StringDataIn + StringLe; 
        }
        
        if(StringAPDU.length() < 8)
        {
            return false;
        }
        
        byteAPDU = atohex(StringAPDU);
        respAPDU = transceives(byteAPDU);
       
        if(mCheckResp.isChecked())
        {
            try
            {
                vShowResponseInterpretation(respAPDU);
            }
            catch (Exception e) 
            {
                clearlog();
                print("Response is not TLV format !!!");
            }
        }
               
        return true;
    }

    private void vShowErrorVaules()
    {
        Context context = getApplicationContext();
        CharSequence text = "C-APDU values ERROR";
        int duration = Toast.LENGTH_LONG;
        HideKbd();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private static void clearlog()
    {
        txtLog.setText("");
    }

    private static void print(String s)
    {
        txtLog.append(s);
        txtLog.append("\r\n");
        return;
    }

    private static String getHexString(byte[] data,int slen) throws Exception
    {
        String szDataStr = "";
        for (int ii=0; ii < slen; ii++) 
        {
            szDataStr += String.format("%02X ", data[ii] & 0xFF);
        }
        return szDataStr;
    }

    private static byte[] atohex(String data)
    {
        String hexchars = "0123456789abcdef";

        data = data.replaceAll(" ","").toLowerCase();
        if (data == null)
        {
            return null;
        }
        byte[] hex = new byte[data.length() / 2];
        
        for (int ii = 0; ii < data.length(); ii += 2)
        {
            int i1 = hexchars.indexOf(data.charAt(ii));
            int i2 = hexchars.indexOf(data.charAt(ii + 1));
            hex[ii/2] = (byte)((i1 << 4) | i2);
        }
        return hex;
    }
    
}
