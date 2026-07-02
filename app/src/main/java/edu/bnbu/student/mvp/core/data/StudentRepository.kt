package edu.bnbu.student.mvp.core.data

import edu.bnbu.student.mvp.core.model.StudentWorkspace
import edu.bnbu.student.mvp.core.network.LoginResponse
import edu.bnbu.student.mvp.core.network.MarkReadResponse
import edu.bnbu.student.mvp.core.network.StudentLoginRequest
import edu.bnbu.student.mvp.core.network.SubmitRecordResponse
import edu.bnbu.student.mvp.core.network.SubmitSportRecordRequest
import edu.bnbu.student.mvp.core.network.SupplementResponse
import edu.bnbu.student.mvp.core.network.SupplementSportRecordRequest

interface StudentRepository {
    /** Synchronous fallback — prefer [loadWorkspaceAsync] for all real code paths. */
    fun loadWorkspace(): StudentWorkspace

    /** Fetch the full student workspace from the backend. */
    suspend fun loadWorkspaceAsync(): StudentWorkspace

    /** Authenticate against the backend. */
    suspend fun login(payload: StudentLoginRequest): LoginResponse

    /** Submit a new sport record. Returns [Result] for error handling. */
    suspend fun submitRecord(payload: SubmitSportRecordRequest): Result<SubmitRecordResponse>

    /** Supplement an existing record. Returns [Result] for error handling. */
    suspend fun supplementRecord(recordId: String, payload: SupplementSportRecordRequest): Result<SupplementResponse>

    /** Mark a notification as read. Returns [Result] for error handling. */
    suspend fun markNotificationRead(id: String): Result<MarkReadResponse>
}
