package sk.kra.files.provider.remote;

import sk.kra.files.provider.remote.IRemoteFileSystem;
import sk.kra.files.provider.remote.IRemoteFileSystemProvider;
import sk.kra.files.provider.remote.IRemotePosixFileAttributeView;
import sk.kra.files.provider.remote.IRemotePosixFileStore;
import sk.kra.files.provider.remote.ParcelableObject;

interface IRemoteFileService {
    IRemoteFileSystemProvider getRemoteFileSystemProviderInterface(String scheme);

    IRemoteFileSystem getRemoteFileSystemInterface(in ParcelableObject fileSystem);

    IRemotePosixFileStore getRemotePosixFileStoreInterface(in ParcelableObject fileStore);

    IRemotePosixFileAttributeView getRemotePosixFileAttributeViewInterface(
        in ParcelableObject attributeView
    );
}
