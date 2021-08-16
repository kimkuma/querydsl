package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        // member1 을 찾아라.
        String qlString = "select m from Member m where m.username = :username";

        Member findMember =
                em.createQuery(qlString, Member.class)
                        .setParameter("username", "member1")
                        .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        // QMember m = new QMember("m");

        Member findMember =
                queryFactory
                        .select(member)
                        .from(member)
                        .where(member.username.eq("member1"))
                        .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember =
                queryFactory
                        .selectFrom(member)
                        .where(member.username.eq("member1").and(member.age.eq(10)))
                        .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember =
                queryFactory
                        .selectFrom(member)
                        .where(member.username.eq("member1"), member.age.eq(10))
                        .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void aggregation() {
        List<Tuple> result =
                queryFactory
                        .select(
                                member.count(),
                                member.age.sum(),
                                member.age.avg(),
                                member.age.max(),
                                member.age.min())
                        .from(member)
                        .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
    }

    /** 팀의 이름과 각 팀의 평균 연력을 구함. */
    @Test
    public void group() {
        List<Tuple> result =
                queryFactory
                        .select(team.name, member.age.avg())
                        .from(member)
                        .join(member.team, team)
                        .groupBy(team.name)
                        .fetch();

        Tuple tupleA = result.get(0);
        Tuple tupleB = result.get(1);

        assertThat(tupleA.get(team.name)).isEqualTo("teamA");
        assertThat(tupleA.get(member.age.avg())).isEqualTo(15);

        assertThat(tupleB.get(team.name)).isEqualTo("teamB");
        assertThat(tupleB.get(member.age.avg())).isEqualTo(35);
    }

    /** 팀 A에 소속된 모든 회원 */
    @Test
    public void join() {
        List<Member> result =
                queryFactory
                        .selectFrom(member)
                        .join(member.team, team)
                        .where(team.name.eq("teamA"))
                        .fetch();

        assertThat(result).extracting("username").containsExactly("member1", "member2");
    }

    /** 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회 */
    @Test
    void join_on_filtering() {
        List<Tuple> result =
                queryFactory
                        .select(member, team)
                        .from(member)
                        .leftJoin(member.team, team)
                        .on(team.name.eq("teamA"))
                        .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /** 나이가 가장 많은 회원 조회 */
    @Test
    void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result =
                queryFactory
                        .selectFrom(member)
                        .where(
                                member.age.eq(
                                        JPAExpressions.select(memberSub.age.max()).from(memberSub)))
                        .fetch();

        assertThat(result).extracting("age").containsExactly(40);
    }

    @Test
    void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result =
                queryFactory
                        .select(
                                member.username,
                                JPAExpressions.select(memberSub.age.avg()).from(memberSub))
                        .from(member)
                        .fetch();
    }

    @Test
    void findDtoByJPQL() {
        List<MemberDto> result =
                em.createQuery(
                                "select new study.querydsl.dto.MemberDto(m.username,m.age) from Member m",
                                MemberDto.class)
                        .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void findDtoBySetter() {
        List<MemberDto> result =
                queryFactory
                        .select(Projections.bean(MemberDto.class, member.username, member.age))
                        .from(member)
                        .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void findDtoByField() {
        List<MemberDto> result =
                queryFactory
                        .select(Projections.fields(MemberDto.class, member.username, member.age))
                        .from(member)
                        .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void findUserDto() {
        List<UserDto> result =
                queryFactory
                        .select(
                                Projections.fields(
                                        UserDto.class, member.username.as("name"), member.age))
                        .from(member)
                        .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    void findDtoByConstructor() {
        List<MemberDto> result =
                queryFactory
                        .select(
                                Projections.constructor(
                                        MemberDto.class, member.username, member.age))
                        .from(member)
                        .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameParam != null) {
            builder.and(member.username.eq(usernameParam));
        }
        if (ageParam != null) {
            builder.and(member.age.eq(ageParam));
        }

        return queryFactory.selectFrom(member).where(builder).fetch();
    }

    @Test
    void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory
                .selectFrom(member)
                .where(allEq(usernameParam,ageParam))
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageParam) {
        return ageParam != null ? member.age.eq(ageParam) : null;
    }

    private BooleanExpression usernameEq(String usernameParam) {
        return usernameParam != null ? member.username.eq(usernameParam) : null;
    }

    private BooleanExpression allEq(String usernameParam, Integer ageParam) {
        return usernameEq(usernameParam).and(ageEq(ageParam));
    }


}