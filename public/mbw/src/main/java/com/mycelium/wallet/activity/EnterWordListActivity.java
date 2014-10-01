/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.google.common.collect.Lists;
import com.mrd.bitlib.crypto.Bip39;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.event.SeedFromWordsCreated;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.KeyCipher;
import com.squareup.otto.Bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EnterWordListActivity extends ActionBarActivity {


   public static void callMe(Activity activity, int requestCode) {
      Intent intent = new Intent(activity, EnterWordListActivity.class);
      activity.startActivityForResult(intent, requestCode);
   }

   private MbwManager _mbwManager;
   private ProgressDialog _progress;
   private TextView enterWordInfo;
   private AutoCompleteTextView acTextView;
   private List<String> enteredWords;
   private boolean usesPassword;
   private int numberOfWords;
   private int currentWordNum;
   private ArrayList<String> basewords;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_import_words);
      _mbwManager = MbwManager.getInstance(this);
      _progress = new ProgressDialog(this);
      enteredWords = new ArrayList<String>();
      enterWordInfo = (TextView) findViewById(R.id.tvEnterWord);
      findViewById(R.id.btDeleteLastWord).setOnClickListener(deleteListener);
      currentWordNum = 1;

      basewords = Lists.newArrayList(Bip39.ENGLISH_WORD_LIST);
      ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, basewords);
      acTextView = (AutoCompleteTextView) findViewById(R.id.tvWordCompleter);
      acTextView.setInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
      acTextView.setThreshold(1);
      acTextView.setAdapter(adapter);
      acTextView.setOnItemClickListener(itemClicked);
      acTextView.addTextChangedListener(charEntered);
      acTextView.setLongClickable(false);

      if (savedInstanceState == null) {
         //only ask if we are not recreating the activity, because of rotation for example
         askForWordNumber();
      }
   }

   private void askForWordNumber() {
      View checkBoxView = View.inflate(this, R.layout.wordlist_checkboxes, null);
      final CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkboxWordlistPassword);
      final RadioButton words12 = (RadioButton) checkBoxView.findViewById(R.id.wordlist12);
      final RadioButton words18 = (RadioButton) checkBoxView.findViewById(R.id.wordlist18);
      final RadioButton words24 = (RadioButton) checkBoxView.findViewById(R.id.wordlist24);

      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle(R.string.import_words_title);
      builder.setMessage(R.string.import_wordlist_questions)
            .setView(checkBoxView)
            .setCancelable(false)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                  usesPassword = checkBox.isChecked();
                  if (words12.isChecked()) {
                     numberOfWords = 12;
                  } else if (words18.isChecked()) {
                     numberOfWords = 18;
                  } else if (words24.isChecked()) {
                     numberOfWords = 24;
                  } else {
                     throw new IllegalStateException("No radiobutton selected in word list import");
                  }
                  acTextView.setHint(getString(R.string.importing_wordlist_enter_next_word, currentWordNum, numberOfWords));
               }
            })
            .show();
   }

   private View.OnClickListener deleteListener = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
         enteredWords.remove(enteredWords.size()-1);
         enterWordInfo.setText(enteredWords.toString());
         acTextView.setText("");
         acTextView.setHint(getString(R.string.importing_wordlist_enter_next_word, --currentWordNum, numberOfWords));
         acTextView.setEnabled(true);
         findViewById(R.id.tvChecksumWarning).setVisibility(View.GONE);
         if (currentWordNum == 1) {
            findViewById(R.id.btDeleteLastWord).setEnabled(false);
         }
      }
   };

   AdapterView.OnItemClickListener itemClicked = new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
         String selection = (String) parent.getItemAtPosition(position);
         addWordToList(selection);
      }
   };

   private void addWordToList(String selection) {
      enteredWords.add(selection);
      enterWordInfo.setText(enteredWords.toString());
      acTextView.setText("");
      if (checkIfDone()) {
         askForPassword();
      } else {
         findViewById(R.id.btDeleteLastWord).setEnabled(true);
      }
   }

   TextWatcher charEntered = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) { }

      @Override
      public void afterTextChanged(Editable s) {
         String word = s.toString();
         if (basewords.contains(word)) {
            addWordToList(word);
         }
      }
   };

   private void askForPassword() {
      if (usesPassword) {
         final EditText pass = new EditText(this);

         AlertDialog.Builder builder = new AlertDialog.Builder(this);
         builder.setTitle(R.string.type_password_title);
         builder.setView(pass)
               .setCancelable(false)
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int id) {
                     calculateSeed(pass.getText().toString());
                  }
               })
               .show();
      } else {
         calculateSeed("");
      }
   }

   private boolean checkIfDone() {
      if (currentWordNum < numberOfWords) {
         acTextView.setHint(getString(R.string.importing_wordlist_enter_next_word, ++currentWordNum, numberOfWords));
         return false;
      }
      if (checksumMatches()) {
         return true;
      } else {
         findViewById(R.id.tvChecksumWarning).setVisibility(View.VISIBLE);
         acTextView.setEnabled(false);
         acTextView.setHint("");
         currentWordNum++; //needed for the delete button to function correctly
         return false;
      }
   }

   private boolean checksumMatches() {
      return Bip39.isValidWordList(enteredWords.toArray(new String[enteredWords.size()]));
   }

   private void calculateSeed(String password) {
      _progress.setCancelable(false);
      _progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      _progress.setMessage(getString(R.string.importing_master_seed_from_wordlist));
      _progress.show();
      new MasterSeedFromWordsAsyncTask(_mbwManager.getEventBus(), enteredWords, password).execute();
   }

   private class MasterSeedFromWordsAsyncTask extends AsyncTask<Void, Integer, UUID> {
      private Bus bus;
      private List<String> wordList;
      private String password;

      public MasterSeedFromWordsAsyncTask(Bus bus, List<String> wordList, String password) {
         this.bus = bus;
         this.wordList = wordList;
         this.password = password;
      }

      @Override
      protected UUID doInBackground(Void... params) {
         try {
            Bip39.MasterSeed masterSeed = Bip39.generateSeedFromWordList(wordList, password);
            _mbwManager.getWalletManager(false).configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher());
            _mbwManager.getMetadataStorage().setMasterKeyBackupState(MetadataStorage.BackupState.VERIFIED);
            return _mbwManager.getWalletManager(false).createAdditionalBip44Account(AesKeyCipher.defaultKeyCipher());
         } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
            throw new RuntimeException(invalidKeyCipher);
         }
      }

      @Override
      protected void onPostExecute(UUID account) {
         bus.post(new SeedFromWordsCreated(account));
      }
   }

   @com.squareup.otto.Subscribe
   public void seedCreated(SeedFromWordsCreated event) {
      _progress.dismiss();
      finishOk(event.account);
   }

   @Override
   public void onResume() {
      _mbwManager.getEventBus().register(this);
      super.onResume();
   }

   @Override
   public void onPause() {
      _progress.dismiss();
      _mbwManager.getEventBus().unregister(this);
      super.onPause();
   }

   private void finishOk(UUID account) {
      Intent result = new Intent();
      result.putExtra(AddAccountActivity.RESULT_KEY, account);
      setResult(RESULT_OK, result);
      finish();
   }


   @Override
   public void onSaveInstanceState(Bundle savedInstanceState)
   {
      super.onSaveInstanceState(savedInstanceState);
      savedInstanceState.putBoolean("usepass", usesPassword);
      savedInstanceState.putInt("index", currentWordNum);
      savedInstanceState.putInt("total", numberOfWords);
      savedInstanceState.putStringArray("entered", enteredWords.toArray(new String[enteredWords.size()]));
   }

   @Override
   public void onRestoreInstanceState(Bundle savedInstanceState)
   {
      super.onRestoreInstanceState(savedInstanceState);
      enteredWords = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("entered")));
      enterWordInfo.setText(enteredWords.toString());
      usesPassword = savedInstanceState.getBoolean("usepass");
      numberOfWords = savedInstanceState.getInt("total");
      currentWordNum = savedInstanceState.getInt("index");
      findViewById(R.id.btDeleteLastWord).setEnabled(currentWordNum > 1);
      if (currentWordNum < numberOfWords) {
         acTextView.setHint(getString(R.string.importing_wordlist_enter_next_word, currentWordNum, numberOfWords));
      } else if (!checksumMatches()) {
         findViewById(R.id.tvChecksumWarning).setVisibility(View.VISIBLE);
         acTextView.setEnabled(false);
         acTextView.setHint("");
      }
   }
}