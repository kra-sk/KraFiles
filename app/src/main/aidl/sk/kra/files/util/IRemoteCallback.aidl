package sk.kra.files.util;

import android.os.Bundle;

interface IRemoteCallback {
    void sendResult(in Bundle result);
}
