package sg.org.bcc.attendance.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.util.SecurityManager
import sg.org.bcc.attendance.util.time.TimeProvider
import java.io.File

@RunWith(RobolectricTestRunner::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class AuthManagerTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<AuthData>
    private lateinit var authManager: AuthManager
    private val timeProvider: TimeProvider = mockk()
    private val securityManager: SecurityManager = mockk()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope(StandardTestDispatcher())
        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
        
        // Use a real serializer but with mocked security manager for simplicity
        val serializer = AuthDataSerializer(securityManager)
        
        dataStore = DataStoreFactory.create(
            serializer = serializer,
            scope = CoroutineScope(testScope.coroutineContext + Job()),
            produceFile = { context.dataStoreFile("test_auth_data.json") }
        )

        // Mock encryption to just return the same bytes
        coEvery { securityManager.encrypt(any(), any()) } answers { firstArg() }
        coEvery { securityManager.decrypt(any(), any()) } answers { firstArg() }
        
        authManager = AuthManager(context, timeProvider, dataStore)
    }

    @After
    fun cleanup() {
        File(context.filesDir, "datastore").deleteRecursively()
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Test
    fun `initial state is unauthenticated`() = testScope.runTest {
        authManager.authState.value shouldBe AuthState.UNAUTHENTICATED
        authManager.isAuthed.value shouldBe false
        authManager.isDemoMode.value shouldBe false
    }

    @Test
    fun `login sets demo token and authenticated state`() = testScope.runTest {
        authManager.login("demo@bethany.sg")
        
        authManager.authState.first() shouldBe AuthState.AUTHENTICATED
        authManager.isAuthed.first() shouldBe true
        authManager.isDemoMode.first() shouldBe true
        authManager.getEmail() shouldBe "demo@bethany.sg"
    }

    @Test
    fun `logout clears all data`() = testScope.runTest {
        authManager.login("demo@bethany.sg")
        authManager.logout()
        
        authManager.authState.first() shouldBe AuthState.UNAUTHENTICATED
        authManager.isAuthed.first() shouldBe false
        authManager.getEmail() shouldBe null
    }

    @Test
    fun `token expired returns true when past expiry`() = testScope.runTest {
        coEvery { timeProvider.now() } returns 2000L
        
        authManager.exchangeCodeForTokens("dummy_code") // This will fail in test due to real network call, so let's use internal saveTokens if it was public or just use a mock response.
        
        // Instead, let's just update the dataStore directly to simulate saved tokens
        dataStore.updateData { 
            it.copy(
                email = "test@bethany.sg",
                accessToken = "access",
                refreshToken = "refresh",
                expiryTime = 1000L // Already expired
            )
        }
        
        authManager.isTokenExpired() shouldBe true
        authManager.authState.first() shouldBe AuthState.EXPIRED
    }
}
