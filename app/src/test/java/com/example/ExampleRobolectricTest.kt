package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `release configuration verifies package id, app name, backup policy, and firebase resources`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("com.mcc.campnidhi", context.packageName)
    assertEquals("CampNidhi", context.getString(R.string.app_name))

    val allowBackupEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    assertFalse("allowBackup must be disabled to prevent private financial data leaks", allowBackupEnabled)

    val dbId = context.getString(R.string.firestore_database_id)
    assertTrue("firestore_database_id must be configured", dbId.isNotBlank())

    val recaptchaKeyId = context.resources.getIdentifier("recaptcha_site_key", "string", context.packageName)
    assertTrue("recaptcha_site_key must be bundled for Release App Check", recaptchaKeyId != 0)
    assertTrue(context.getString(recaptchaKeyId).isNotBlank())

    val webClientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    assertTrue("default_web_client_id must be generated for com.mcc.campnidhi Google Sign-In", webClientIdRes != 0)
    assertTrue(context.getString(webClientIdRes).isNotBlank())
  }
}
