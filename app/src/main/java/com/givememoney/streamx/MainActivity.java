package com.givememoney.streamx;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity  {

    //объявляем кнопки и прочее
    ImageButton capture, toggleFlash, flipCamera;
    //предварительный просмотр камеры
    private PreviewView previewView;
    //задняя камера
    int cameraFacing= CameraSelector.LENS_FACING_BACK;

    //средство запуска результатов действия, запрашиваем разрешение, если результат тру то выбираем cameraFacing
    private final ActivityResultLauncher<String> activityResultLauncher=registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean result) {
                    if (result){
                        startCamera(cameraFacing);
                    }
                }
            }
    );

    //создаем сцену
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView=findViewById(R.id.cameraPreview);
        capture=findViewById(R.id.capture);
        toggleFlash=findViewById(R.id.toggleFlash);
        flipCamera=findViewById(R.id.flipCamera);

        //если не разрешена камера, то выполняем  ActivityResultContrac из activityResultLauncher
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        }
        else{
            //запускаем камеру
            startCamera(cameraFacing);
        }

        //слушатель переключения камеры
        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK){
                    cameraFacing=CameraSelector.LENS_FACING_FRONT;
                }
                else {
                    cameraFacing=CameraSelector.LENS_FACING_BACK;
                }
                //запускаем камеру
                startCamera(cameraFacing);
            }
        });
    }

    //запуск камеры
    public void startCamera(int cameraFacing) {
        //выбираем соотношение сторон
        int aspectRatio = aspectRatio(previewView.getWidth(), previewView.getHeight());
        //интерфейс прослушивания будущего, которому передаем состояние камеры
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        //добавляем слушателя
        listenableFuture.addListener(() -> {
            try {
                //получаем состояние?
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) listenableFuture.get();

                //получаем поток с камеры на экран с соотношением сторон
                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();

                //устанавливаем режим захвата изображения,устанавливаем поворот
                ImageCapture imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();

                //выбираем камеру из cameraFacing
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing).build();

                //закрываем каждые открытые камеры
                cameraProvider.unbindAll();

                //Привязываем коллекцию вариантов использования к владельцу жизненного цикла
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                //устанавливаем слушатель
                capture.setOnClickListener(new View.OnClickListener() {
                    //если кнопка нажата то проверяем разрешение на запись
                    @Override
                    public void onClick(View view) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        }
                        takePicture(imageCapture);
                    }
                });

                //слушатель вспышки
                toggleFlash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setFlashIcon(camera);
                    }
                });

                //устанавливаем предворительный просмотр, путем получения предварительного просмотра из previewView
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //делаем фото
    public void takePicture(ImageCapture imageCapture) {
        //путь в котором сохраняется фото
        final File file = new File(getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");

        //опции для сохранения снятого изображения, создаем параметры для захв. изображения в файл.
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();

        //делаем фото
        imageCapture.takePicture(outputFileOptions, Executors.newCachedThreadPool(), new ImageCapture.OnImageSavedCallback() {

            //если фото сохранено делаем уведомление
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Image saved at: " + file.getPath(), Toast.LENGTH_SHORT).show();
                    }
                });
                startCamera(cameraFacing);
            }

            //если фото нет то выводим ошибку
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Failed to save: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                startCamera(cameraFacing);
            }
        });
    }

    //устанавливаем иконку вспышки
    private void setFlashIcon(Camera camera) {
        //если вспышка имеется
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.round_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.round_flash_on_24);
            }
        } else {
            //если нет то пишем сообщение
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Flash is not available currently", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    //устанавливаем разрешение
    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }
}