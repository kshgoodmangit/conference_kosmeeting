import { useImperativeHandle, type ComponentPropsWithRef } from 'react';
import { useModalDrag } from '../hooks/useModalDrag';

/** Keeps the existing layout and DOM ref; mark the header with data-modal-drag-handle. */
export function DraggableModal({ ref, ...props }: ComponentPropsWithRef<'div'>) {
    const { modalRef } = useModalDrag(true, 'offset');
    useImperativeHandle(ref, () => modalRef.current!, [modalRef]);
    return <div {...props} ref={modalRef} data-draggable-modal />;
}

/** Form-root modals retain submit events and native form semantics. */
export function DraggableModalForm({ ref, ...props }: ComponentPropsWithRef<'form'>) {
    const { modalRef } = useModalDrag<HTMLFormElement>(true, 'offset');
    useImperativeHandle(ref, () => modalRef.current!, [modalRef]);
    return <form {...props} ref={modalRef} data-draggable-modal />;
}
