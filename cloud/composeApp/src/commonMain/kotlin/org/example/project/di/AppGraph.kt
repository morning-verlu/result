package cn.verlu.cloud.di

import cn.verlu.cloud.data.auth.AuthRepository
import cn.verlu.cloud.data.auth.SupabaseAuthRepository
import cn.verlu.cloud.data.files.DefaultFileRepository
import cn.verlu.cloud.data.files.FileRepository
import cn.verlu.cloud.data.files.SqlDelightTransferRepository
import cn.verlu.cloud.data.files.TransferRepository
import cn.verlu.cloud.data.friends.CloudFriendRepository
import cn.verlu.cloud.data.friends.SupabaseCloudFriendRepository
import cn.verlu.cloud.data.storage.ObjectStoragePort
import cn.verlu.cloud.data.storage.edge.CloudEdgeFunctionAdapter
import cn.verlu.cloud.data.local.DatabaseDriverFactory
import cn.verlu.cloud.data.remote.CloudSupabase
import cn.verlu.cloud.db.CloudDatabase
import cn.verlu.cloud.domain.auth.AuthUseCases
import cn.verlu.cloud.domain.auth.BeginDesktopQrLogin
import cn.verlu.cloud.domain.auth.HandleDeepLink
import cn.verlu.cloud.domain.auth.ObserveQrApproval
import cn.verlu.cloud.domain.auth.ObserveSession
import cn.verlu.cloud.domain.auth.ResetPassword
import cn.verlu.cloud.domain.auth.SignInEmail
import cn.verlu.cloud.domain.auth.SignInOAuth
import cn.verlu.cloud.domain.auth.SignInWithQrToken
import cn.verlu.cloud.domain.auth.SignOut
import cn.verlu.cloud.domain.auth.SignUpEmail

class AppGraph {
    private val driverFactory = DatabaseDriverFactory()
    private val database = CloudDatabase(driverFactory.createDriver())
    private val supabase = CloudSupabase.client

    /**
     * 远程对象存储：通过 Supabase Edge Function `cloud-files` 中转，
     * S3 密钥（缤纷云）保存在 Supabase Secrets，客户端只持有用户 JWT。
     * 若未来切换服务商，只需替换此处实现即可。
     */
    val objectStorage: ObjectStoragePort = CloudEdgeFunctionAdapter(supabase)

    val authRepository: AuthRepository = SupabaseAuthRepository(supabase)
    val fileRepository: FileRepository = DefaultFileRepository(database, objectStorage)
    val transferRepository: TransferRepository = SqlDelightTransferRepository(database)
    /** 用于"分享给好友"功能：从 Talk 共享的 friendships/profiles/messages 表读写。 */
    val friendRepository: CloudFriendRepository = SupabaseCloudFriendRepository(supabase)

    val authUseCases = AuthUseCases(
        observeSession = ObserveSession(authRepository),
        signInEmail = SignInEmail(authRepository),
        signUpEmail = SignUpEmail(authRepository),
        resetPassword = ResetPassword(authRepository),
        signInOAuth = SignInOAuth(authRepository),
        handleDeepLink = HandleDeepLink(authRepository),
        beginDesktopQrLogin = BeginDesktopQrLogin(authRepository),
        observeQrApproval = ObserveQrApproval(authRepository),
        signInWithQrToken = SignInWithQrToken(authRepository),
        signOut = SignOut(authRepository),
    )
}
