package mx.com.huawei.hms.audioeditor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.huawei.hms.audioeditor.common.agc.HAEApplication;
import com.huawei.hms.audioeditor.sdk.AudioSeparationCallBack;
import com.huawei.hms.audioeditor.sdk.HAEAudioSeparationFile;
import com.huawei.hms.audioeditor.sdk.HAEErrorCode;
import com.huawei.hms.audioeditor.sdk.materials.network.SeparationCloudCallBack;
import com.huawei.hms.audioeditor.sdk.materials.network.inner.bean.SeparationBean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import mx.com.huawei.hms.audioeditor.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    public final String[] EXTERNAL_PERMS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
    };
    String mParam1 = "";

    private ActivityMainBinding binding;
    private ProgressDialog progressDialog;
    private boolean isProcessing;

    private List<String> instruments;
    HAEAudioSeparationFile haeAudioSeparationFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        checkReadPermission();
    }

    private void init() {
        try {
            SaveFile();
            binding.btnSplit.setOnClickListener(view -> {
                SepararAudio();
            });
            initProgress();
            initAudioKitEditor();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getBaseContext(), "ERROR. " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void SaveFile() throws IOException {
        InputStream in = getResources().openRawResource(R.raw.rap1);    // You can replace it with one of the files included in the raw folder.
        String op = "/storage/emulated/0/AudioEditor/separate/";
        String fileName = "trackTest.mp3";
        File nf = new File(op);
        if(!nf.exists())
            nf.mkdirs();
        FileOutputStream out = new FileOutputStream(op + fileName);
        byte[] buff = new byte[1024];
        int read = 0;
        try {
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
        mParam1 = op + fileName;
    }

    //region Audio Editor Kit
    private void initAudioKitEditor(){
        HAEApplication.getInstance().setApiKey("REMOVED - Fill with your api key");
        //EdicionAudio();
        haeAudioSeparationFile = new HAEAudioSeparationFile();
        instruments = new ArrayList<>();
        haeAudioSeparationFile.getInstruments(callBackInstruments);
    }

    private void SepararAudio() {
        isProcessing = true;
        showProgress();
        haeAudioSeparationFile.setInstruments(instruments);
        String inAudioPath = mParam1;
        String outAudioDir = mParam1.substring(0, mParam1.lastIndexOf('/')) + "/converted/";
        String outAudioName = mParam1.substring(mParam1.lastIndexOf('/')+1, mParam1.lastIndexOf('.')) + "-Converted";
        haeAudioSeparationFile.startSeparationTasks(inAudioPath, outAudioDir, outAudioName, new AudioSeparationCallBack() {
            @Override
            public void onResult(SeparationBean separationBean) {
                Log.d("AudioSeparationCallBack", "haeAudioSeparationFile.startSeparationTasks.AudioSeparationCallBack.onResult");
                runOnUiThread(
                        () -> {
                            Toast.makeText(getBaseContext(),
                                    separationBean.getInstrument()
                                            + " Success: "
                                            + separationBean.getOutAudioPath(), Toast.LENGTH_LONG).show();
                        });
            }

            @Override
            public void onFinish(List<SeparationBean> list) {
                Log.d("AudioSeparationCallBack", "haeAudioSeparationFile.startSeparationTasks.AudioSeparationCallBack.onFinish");
                runOnUiThread(
                        () -> {
                            isProcessing = false;
                            hideProgress();
                        });
            }

            @Override
            public void onFail(int errorCode) {
                Log.d("AudioSeparationCallBack", "haeAudioSeparationFile.startSeparationTasks.AudioSeparationCallBack.onFail: " + String.valueOf(errorCode));
                runOnUiThread(
                        () -> {
                            isProcessing = false;
                            hideProgress();
                            if (errorCode != HAEErrorCode.FAIL_FILE_EXIST)
                                Toast.makeText(getBaseContext(), "ErrorCode : " + errorCode, Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(getBaseContext(), getResources().getString(R.string.file_exists), Toast.LENGTH_LONG).show();
                        });
            }

            @Override
            public void onCancel() {
                Log.d("AudioSeparationCallBack", "haeAudioSeparationFile.startSeparationTasks.AudioSeparationCallBack.onCancel");
                runOnUiThread(
                        () -> {
                            isProcessing = false;
                            Toast.makeText(getBaseContext(), "Cancel !", Toast.LENGTH_SHORT).show();
                            hideProgress();
                        });
            }
        });
    }

    private SeparationCloudCallBack callBackInstruments = new SeparationCloudCallBack<List<SeparationBean>>() {
        @Override
        public void onFinish(List<SeparationBean> response) {
            Log.d("SeparationCloudCallBack", "onFinish" );
            if (response != null && !response.isEmpty()) {
                for (SeparationBean separationBean : response)
                    instruments.add(separationBean.getInstrument());
                Toast.makeText(getApplicationContext(), String.valueOf(response.size())  + " instruments available", Toast.LENGTH_SHORT).show();
                binding.btnSplit.setEnabled(true);
            }
        }

        @Override
        public void onError(int errorCode) {
            Log.d("SeparationCloudCallBack", "onError: " + String.valueOf(errorCode));
        }
    };
    //endregion

    // region Progress
    private void initProgress() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(true);
        progressDialog.setTitle(getResources().getString(R.string.in_progress));
        progressDialog.setMax(100);
    }
    private void showProgress() {
        if (progressDialog != null)
            progressDialog.show();
    }
    private void hideProgress() {
        if (progressDialog != null)
            progressDialog.hide();
    }
    //endregion

    //region Permission
    private void checkReadPermission() {
        if (  ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[0])
                + ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[1])
                + ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[2])
                + ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[3])
                + ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[4])
                + ContextCompat.checkSelfPermission(getApplicationContext(), EXTERNAL_PERMS[5])
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, EXTERNAL_PERMS, 1);
        }
        else
            init();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull java.lang.String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0) {
            int res = 0;
            for(int i=0; i<grantResults.length; i++)
                res += grantResults[i];
            if (res == 0){
                init();
            }
            else
                Toast.makeText(this, "Some of the permissions have NOT been authorized.", Toast.LENGTH_SHORT).show();
        }
    }
    //endregion
}