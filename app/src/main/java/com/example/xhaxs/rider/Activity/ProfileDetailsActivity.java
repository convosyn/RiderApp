package com.example.xhaxs.rider.Activity;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;

import android.widget.TextView;
import android.widget.Toast;

import com.example.xhaxs.rider.AppUtils;
import com.example.xhaxs.rider.Datatype.UserSumData;
import com.example.xhaxs.rider.LogHandle;
import com.example.xhaxs.rider.R;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileDetailsActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final String MALE_STRING = "Male";
    public static final String FEMALE_STRING = "Female";
    public static final String OTHER_STRING = "Other";

    public static final int GALLERY_PICK = 102;

    public static final String[] AVAILABLE_GENDERS = {MALE_STRING, FEMALE_STRING, OTHER_STRING};

    private CircleImageView mProfilePic;
    private EditText mUserName;
//    private TextView mCountryCode;
//    private EditText mPhoneNumber;
    private Button mSubmitDetails;

    private TextView mGenderTextView;
    private FirebaseUser firebaseUser;

    private DatabaseReference mDatabase;
    private StorageReference mStorageRef, mImageRef;
    private FirebaseStorage mStorage;
    private UploadTask uploadTask;
    private ProgressDialog progressDialog;

    private Uri selectImageUri;
    private String userNameFinal;
//    private String countryCodeFinal;
//    private String phoneNumberFinal;

    private int genderFinal;
    private String downloadUrl;

    private ProgressBar progressBar;

    private String phonenumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_details);

        getSupportActionBar().setTitle("Profile Details");

        firebaseUser = LogHandle.checkLogin(FirebaseAuth.getInstance(), this);

        mGenderTextView = findViewById(R.id.tv_submit_select_gender);
        mProfilePic = findViewById(R.id.iv_submit_profile_pic);
        mUserName = findViewById(R.id.et_sumit_profile_name);

        progressBar = findViewById(R.id.pb_apd_progress_bar);
        progressBar.setVisibility(View.GONE);

        mSubmitDetails = findViewById(R.id.b_submit_profile_od);


        mSubmitDetails.setClickable(true);

//        mCountryCode = findViewById(R.id.et_sumit_profile_country_code);
//        countryCodeFinal = mCountryCode.getText().toString();
//        mPhoneNumber = findViewById(R.id.et_sumit_profile_phone);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        genderFinal = 0;

        mImageRef = FirebaseStorage.getInstance().getReference().child(AppUtils.PROFILE_IMAGE_FOLDER_STRING);

        mGenderTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ProfileDetailsActivity.this)
                        .setTitle("Select Gender")
                        .setSingleChoiceItems(AVAILABLE_GENDERS, genderFinal, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                genderFinal = which;
                                mGenderTextView.setText(AVAILABLE_GENDERS[genderFinal]);
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
        });

        mProfilePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                Intent intent = new Intent();
//                intent.setType("image/*");
//                intent.setAction(Intent.ACTION_GET_CONTENT);
//                if (intent.resolveActivity(getPackageManager()) != null) {
//                    startActivityForResult(intent, GALLERY_PICK);
//                }

                CropImage.activity()
                        .setGuidelines(CropImageView.Guidelines.ON)
                        .setAspectRatio(1, 1)
                        .start(ProfileDetailsActivity.this);
            }
        });

        mUserName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s.toString();
                if (TextUtils.isEmpty(value) == false) {
                    userNameFinal = value;
                } else {
                    mUserName.setError("Enter valid name");
                    mUserName.requestFocus();
                    userNameFinal=null;
                    return;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSubmitDetails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                progressBar.setVisibility(View.VISIBLE);
                mSubmitDetails.setClickable(false);
                
                if (!TextUtils.isEmpty(userNameFinal)
                        && (genderFinal == 0 || genderFinal == 1 || genderFinal == 2)
                        ){


                    if (firebaseUser == null) {
                        // user is not logged in
                        // send him to login page
                        Intent i = new Intent(ProfileDetailsActivity.this, LoginActivity.class);
                        startActivity(i);
                        finish();
                    } else {


                        String imageExt;

                        if(selectImageUri != null){

                            final Bitmap imageBitmap = AppUtils.reduceImageSize(ProfileDetailsActivity.this, selectImageUri);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

                            final int imageSize = imageBitmap.getByteCount();

                            final byte[] uploadImageAsByteArray = baos.toByteArray();

                            mProfilePic.setImageBitmap(imageBitmap);
//                            ContentResolver cr = getContentResolver();
//                            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
//                            imageExt = mimeTypeMap.getExtensionFromMimeType(cr.getType(selectImageUri));

                            mStorageRef = mImageRef.child(firebaseUser.getUid() + "_" + UUID.randomUUID().toString() + ".jpeg");

                            UploadTask uploadTask = mStorageRef.putBytes(uploadImageAsByteArray);

                            Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                                @Override
                                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                                    if(!task.isSuccessful()){
                                        throw task.getException();
                                    }
                                    return mStorageRef.getDownloadUrl();
                                }
                            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    if(task.isSuccessful()){
                                        Toast.makeText(ProfileDetailsActivity.this,"Profile Image Updated", Toast.LENGTH_LONG).show();
                                        Uri uri = task.getResult();

                                        Log.d("-------", "++++++++++\n\t" + uri.toString() + "------------\n\t");

                                        updateDatabase(true, uri, imageSize);
                                    }
                                }
                            });

                         } else {

                            updateDatabase(false, null, -1);

                        }

                    }
                } else {
                    Toast.makeText(ProfileDetailsActivity.this, "Please provide complete details.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void updateDatabase(final boolean uploadProfilePic, final Uri photoUri, final int imageSize){

        UserProfileChangeRequest userProfileChangeRequest;

        userProfileChangeRequest = new UserProfileChangeRequest.Builder()
                .setDisplayName(userNameFinal)
                .build();

        firebaseUser.updateProfile(userProfileChangeRequest)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {


                        if(task.isSuccessful()) {

                            mDatabase = FirebaseDatabase.getInstance().getReference("Users/" + firebaseUser.getUid());
                            HashMap<String, Object> childUpdates = new HashMap<>();

                            childUpdates.put(AppUtils.USER_NAME_STRING, userNameFinal);
                            childUpdates.put(AppUtils.EMAIL_STRING, firebaseUser.getEmail());
                            childUpdates.put(AppUtils.GENDER_STRING, genderFinal);
                            childUpdates.put(AppUtils.PHONE_VERIFIED_STRING, false);
                            if(uploadProfilePic == true) {
                                childUpdates.put(AppUtils.PROFILE_PIC_URL_STRING, photoUri.toString());
                                childUpdates.put(AppUtils.PROFILE_PIC_SIZE_STRING, imageSize);
                            }

                            mDatabase.updateChildren(childUpdates).addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if(task.isSuccessful()){

                                        progressBar.setVisibility(View.GONE);

                                        Intent intent = new Intent(ProfileDetailsActivity.this, PhoneNumberActivity.class);
                                        intent.putExtra(AppUtils.CURRENT_USER_STRING,
                                                new UserSumData(firebaseUser.getUid(), userNameFinal, firebaseUser.getEmail())
                                        );
                                        startActivity(intent);
                                        finish();
                                        mSubmitDetails.setClickable(true);
                                    } else {
                                        Toast.makeText(ProfileDetailsActivity.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });

                        } else {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(ProfileDetailsActivity.this, "Error Saving Data!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == GALLERY_PICK && resultCode == RESULT_OK) {
//            selectImageUri = data.getData();
//            if (null != selectImageUri) {
//                String path = getPathFromURI(selectImageUri);
//                mProfilePic.setImageURI(selectImageUri);
//            }else {
//                selectImageUri=null;
//            }

//            CropImage.activity()
//                    .setGuidelines(CropImageView.Guidelines.ON)
//                    .setAspectRatio(1, 1)
//                    .start(this);
        }
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if(resultCode == RESULT_OK){
                selectImageUri = result.getUri();
                mProfilePic.setImageURI(selectImageUri);
            } else  {
                Toast.makeText(ProfileDetailsActivity.this, "Error cropping", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public String getPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }

    public void close() {
        finish();
    }
}
