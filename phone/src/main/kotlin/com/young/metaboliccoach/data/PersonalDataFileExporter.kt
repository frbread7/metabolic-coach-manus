package com.young.metaboliccoach.data

import android.content.Context
import android.net.Uri
import com.young.metaboliccoach.background.PhoneDataMutationGate
import com.young.metaboliccoach.core.domain.PersonalDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PersonalDataFileExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val personalDataRepository: PersonalDataRepository,
    private val mutationGate: PhoneDataMutationGate,
) {
    suspend fun export(uri: Uri) {
        withContext(Dispatchers.IO) {
            val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                "The selected document cannot be opened for writing."
            }
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                mutationGate.withLock {
                    personalDataRepository.writeJsonExport(
                        exportedAtEpochMillis = System.currentTimeMillis(),
                        destination = writer,
                    )
                }
            }
        }
    }
}
