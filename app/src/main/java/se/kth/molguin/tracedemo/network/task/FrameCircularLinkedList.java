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

    public byte[] getCurrent() {
        if (this.head == null) return null;
        return head.data;
    }

    public void stepForward() {
        if (this.head == null) return;
        this.head = this.head.next;
    }

    public void stepBackward() {
        if (this.head == null) return;
        this.head = this.head.previous;
    }

    public void rewind(int count) {
        if (count < 0) return;
        for (int i = 0; i < count; i++)
            this.stepBackward();
    }

    public void fastForward(int count) {
        if (count < 0) return;
        for (int i = 0; i < count; i++)
            this.stepForward();
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

                //byte[] data = head.data;
                head = head.next;
                return head.data;
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
