package archive

import (
	"context"
	"io/ioutil"
	"os"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"
	"github.com/wings-software/portal/commons/go/lib/filesystem"
	"github.com/wings-software/portal/commons/go/lib/logs"
	"go.uber.org/zap"
)

const (
	fileText1 = "hello"
	fileText2 = "golang"
	fileText3 = "world"
)

func check(e error) {
	if e != nil {
		panic(e)
	}
}

// File structure
// /tmp/test_archive/
//                   folder1/
//							 file1	=> Text(fileText1)
//							 folder2/
//									 file2	=> Text(fileText2)
//					 folder3/
//							 file3	=> Text(fileText3)
func setupFileStructure() {
	err := os.MkdirAll("/tmp/test_archive/folder1/folder2", 0755)
	check(err)
	err = os.MkdirAll("/tmp/test_archive/folder3", 0755)
	check(err)

	// create file1
	f1 := []byte(fileText1)
	err = ioutil.WriteFile("/tmp/test_archive/folder1/file1", f1, 0644)
	check(err)

	// create file2
	f2 := []byte(fileText2)
	err = ioutil.WriteFile("/tmp/test_archive/folder1/folder2/file2", f2, 0644)
	check(err)

	// create file3
	f3 := []byte(fileText3)
	err = ioutil.WriteFile("/tmp/test_archive/folder3/file3", f3, 0644)
	check(err)
}

func deleteFileStructure() {
	err := os.RemoveAll("/tmp/test_archive")
	check(err)
}

func checkFileStructure(t *testing.T) {
	b, err := ioutil.ReadFile("/tmp/test_archive/folder1/file1")
	check(err)
	assert.Equal(t, string(b), fileText1)

	b, err = ioutil.ReadFile("/tmp/test_archive/folder1/folder2/file2")
	check(err)
	assert.Equal(t, string(b), fileText2)

	b, err = ioutil.ReadFile("/tmp/test_archive/folder3/file3")
	check(err)
	assert.Equal(t, string(b), fileText3)
}

func TestArchiveUnarchive(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	dstFilePath := "/tmp/test_archive_dst"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewOSFileSystem(log)

	setupFileStructure()
	a := NewArchiver(GzipFormat, fs, log)

	srcFilePaths := []string{"/tmp/test_archive/folder*"}
	err := a.Archive(srcFilePaths, dstFilePath)
	assert.Equal(t, nil, err)

	deleteFileStructure()
	err = a.Unarchive(dstFilePath, "")
	assert.Equal(t, nil, err)

	checkFileStructure(t)
	deleteFileStructure()
	err = os.RemoveAll(dstFilePath)
	check(err)
}

func TestArchiveInvalidDestination(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	dstFilePath := "/tmp/test_archive_dst1"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewMockFileSystem(ctrl)
	a := NewArchiver(TarFormat, fs, log)

	fs.EXPECT().Create(dstFilePath).Return(nil, os.ErrExist)
	srcFilePaths := []string{"/tmp/test_archive1/folder*"}
	err := a.Archive(srcFilePaths, dstFilePath)
	assert.Equal(t, err, os.ErrExist)
}

func TestArchiveInvalidSrcPath(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	dstFilePath := "/tmp/test_archive_dst1"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewOSFileSystem(log)
	a := NewArchiver(GzipFormat, fs, log)

	// Invalid source path
	srcFilePaths := []string{"~home"}
	err := a.Archive(srcFilePaths, dstFilePath)
	assert.NotEqual(t, err, nil)

	// Invalid source path
	srcFilePaths = []string{"["}
	err = a.Archive(srcFilePaths, dstFilePath)
	assert.NotEqual(t, err, nil)

	os.RemoveAll(dstFilePath)
}

func TestAddToArchive(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	// create file1
	srcFile := "/tmp/test_invalid_archive"
	f := []byte(fileText1)
	err := ioutil.WriteFile(srcFile, f, 0333)
	check(err)

	dstFilePath := "/tmp/test_invalid_archive_dst"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewOSFileSystem(log)

	a := NewArchiver(TarFormat, fs, log)

	srcFilePaths := []string{srcFile}
	err = a.Archive(srcFilePaths, dstFilePath)
	assert.NotEqual(t, err, nil)

	err = os.RemoveAll(srcFile)
	check(err)
	err = os.RemoveAll(dstFilePath)
	check(err)
}

func TestUnarchiveInvalidSource(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	srcFilePath := "/tmp/test_archive_dst2"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewMockFileSystem(ctrl)
	a := NewArchiver(GzipFormat, fs, log)

	fs.EXPECT().Open(srcFilePath).Return(nil, os.ErrPermission)
	err := a.Unarchive(srcFilePath, "")
	assert.Equal(t, err, os.ErrPermission)
}

func TestUnarchiveInvalidType(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	srcFilePath := "/tmp/test_archive3"
	f := []byte(fileText1)
	err := ioutil.WriteFile(srcFilePath, f, 0644)
	check(err)

	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewOSFileSystem(log)
	a := NewArchiver(TarFormat, fs, log)

	err = a.Unarchive(srcFilePath, "")
	assert.NotEqual(t, err, nil)

	err = os.RemoveAll(srcFilePath)
	check(err)
}

func TestArchiveUnarchiveFoldersOnly(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	dstFilePath := "/tmp/test_archive_dst1"
	logger, _ := logs.GetObservedLogger(zap.ErrorLevel)
	log := logger.Sugar()
	fs := filesystem.NewOSFileSystem(log)

	err := os.MkdirAll("/tmp/test_archive1/folder1/folder2", 0755)
	check(err)
	a := NewArchiver(TarFormat, fs, log)

	srcFilePaths := []string{"/tmp/test_archive1", "/tmp/test_archive1"}
	err = a.Archive(srcFilePaths, dstFilePath)
	assert.Equal(t, nil, err)

	deleteFileStructure()
	err = a.Unarchive(dstFilePath, "")
	assert.Equal(t, nil, err)

	err = os.RemoveAll(dstFilePath)
	check(err)
	err = os.RemoveAll("/tmp/test_archive1")
	check(err)
}
