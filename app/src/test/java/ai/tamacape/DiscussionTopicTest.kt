package ai.tamacape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscussionTopicTest {
    @Test fun spokenSubjectCanDifferFromTheCurrentQuestion() {
        assertEquals(1, discussionTopic(0, "차 문은 어른이 확인해요"))
        assertEquals(0, discussionTopic(0, "안전벨트를 매요"))
    }

    @Test fun unrelatedOrAmbiguousAnswersDoNotInventATopic() {
        assertNull(discussionTopic(0, "같이 놀아요"))
        assertNull(discussionTopic(0, "벨트와 도어"))
    }
}
