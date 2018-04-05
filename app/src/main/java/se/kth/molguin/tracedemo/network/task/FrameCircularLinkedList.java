package se.kth.molguin.tracedemo.network.task;

import android.support.annotation.NonNull;

import java.util.Iterator;

public class FrameCircularLinkedList implements Iterable<byte[]> {

    private Frame head;

    public FrameCircularLinkedList() {
        this.head = null;
    }

    public void put(byte[] frame) {
        Frame f = new Frame(frame);
        if (this.head == null) {
            this.head = f;

            // circularity:
            f.next = f;
            f.previous = f;
        } else {
            Frame tmp = this.head.previous;
            this.head.previous = f;
            tmp.next = f;
            f.previous = tmp;
            f.next = this.head;
        }
    }

    @NonNull
    @Override
    public Iterator<byte[]> iterator() {
        return new Iterator<byte[]>() {
            @Override
            public boolean hasNext() {
                return FrameCircularLinkedList.this.head != null;
            }

            @Override
            public byte[] next() {
                if (FrameCircularLinkedList.this.head == null)
                    return null;

                byte[] data = head.data;
                head = head.next;
                return data;
            }
        };
    }

    private static class Frame {
        Frame next;
        Frame previous;
        private byte[] data;

        Frame(byte[] data) {
            this.data = data;
            this.next = null;
            this.previous = null;
        }

        public byte[] getData() {
            return data;
        }
    }
}
