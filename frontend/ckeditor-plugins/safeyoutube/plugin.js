(function () {
    'use strict';

    var VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;
    var YOUTUBE_HOSTS = {
        'youtube.com': true,
        'www.youtube.com': true,
        'm.youtube.com': true,
        'youtube-nocookie.com': true,
        'www.youtube-nocookie.com': true
    };

    function extractVideoId(value) {
        var normalized = String(value || '').trim();
        if (!normalized) {
            return null;
        }
        if (!/^https?:\/\//i.test(normalized)) {
            normalized = 'https://' + normalized;
        }

        var parsed;
        try {
            parsed = new URL(normalized);
        } catch (error) {
            return null;
        }

        if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
            return null;
        }
        if (parsed.username || parsed.password || (parsed.port && parsed.port !== '443')) {
            return null;
        }

        var host = parsed.hostname.toLowerCase();
        var videoId = null;
        if (host === 'youtu.be') {
            videoId = parsed.pathname.split('/').filter(Boolean)[0] || null;
        } else if (YOUTUBE_HOSTS[host]) {
            if (parsed.pathname === '/watch') {
                videoId = parsed.searchParams.get('v');
            } else {
                var pathMatch = parsed.pathname.match(/^\/(?:embed|shorts)\/([A-Za-z0-9_-]{11})\/?$/);
                videoId = pathMatch ? pathMatch[1] : null;
            }
        }

        return videoId && VIDEO_ID_PATTERN.test(videoId) ? videoId : null;
    }

    CKEDITOR.plugins.add('safeyoutube', {
        init: function (editor) {
            editor.addCommand('safeYoutube', new CKEDITOR.dialogCommand('safeYoutube', {
                allowedContent: 'iframe[!src,!width,!height,title,frameborder,loading,referrerpolicy,allow,allowfullscreen]'
            }));
            editor.ui.addButton('SafeYoutube', {
                label: 'YouTube 영상',
                command: 'safeYoutube',
                toolbar: 'insert,30',
                icon: this.path + 'icons/safeyoutube.svg'
            });

            CKEDITOR.dialog.add('safeYoutube', function () {
                return {
                    title: 'YouTube 영상 삽입',
                    minWidth: 440,
                    minHeight: 90,
                    contents: [{
                        id: 'youtube',
                        label: 'YouTube 영상',
                        elements: [{
                            type: 'text',
                            id: 'url',
                            label: 'YouTube 영상 주소',
                            required: true,
                            validate: function () {
                                if (!extractVideoId(this.getValue())) {
                                    alert('올바른 YouTube 영상 주소를 입력해 주세요.');
                                    return false;
                                }
                                return true;
                            },
                            setup: function () {
                                this.setValue('');
                            }
                        }, {
                            type: 'html',
                            html: '<p style="margin-top:8px;color:#666">youtube.com, youtu.be, Shorts 주소를 사용할 수 있습니다.</p>'
                        }]
                    }],
                    onShow: function () {
                        this.getContentElement('youtube', 'url').setValue('');
                        this.getContentElement('youtube', 'url').focus();
                    },
                    onOk: function () {
                        var videoId = extractVideoId(this.getValueOf('youtube', 'url'));
                        if (!videoId) {
                            return false;
                        }

                        var iframe = CKEDITOR.dom.element.createFromHtml(
                            '<iframe src="https://www.youtube-nocookie.com/embed/' + videoId + '"' +
                            ' width="640" height="360" title="YouTube 영상" frameborder="0"' +
                            ' loading="lazy" referrerpolicy="strict-origin-when-cross-origin"' +
                            ' allow="encrypted-media; picture-in-picture" allowfullscreen></iframe>'
                        );
                        editor.insertElement(iframe);
                    }
                };
            });
        }
    });
}());
