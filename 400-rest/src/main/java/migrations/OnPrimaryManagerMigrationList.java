package migrations;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.experimental.UtilityClass;
import migrations.all.DeleteGitFileActivityAndGitFileAcitivitySummary;
import migrations.all.RefactorTheFieldsInGitSyncError;
import migrations.all.SyncNewFolderForConfigFiles;
import migrations.all.TemplateLibraryYamlOnPrimaryManagerMigration;
import migrations.gitsync.SetQueueKeyYamChangeSetMigration;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class OnPrimaryManagerMigrationList {
  public static List<Pair<Integer, Class<? extends OnPrimaryManagerMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends OnPrimaryManagerMigration>>>()
        .add(Pair.of(1, SyncNewFolderForConfigFiles.class))
        .add(Pair.of(2, TemplateLibraryYamlOnPrimaryManagerMigration.class))
        .add(Pair.of(3, RefactorTheFieldsInGitSyncError.class))
        .add(Pair.of(4, BaseMigration.class))
        .add(Pair.of(5, SetQueueKeyYamChangeSetMigration.class))
        .add(Pair.of(6, DeleteGitFileActivityAndGitFileAcitivitySummary.class))
        .build();
  }
}
